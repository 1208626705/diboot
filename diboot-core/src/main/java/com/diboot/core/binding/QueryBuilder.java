/*
 * Copyright (c) 2015-2020, www.dibo.ltd (service@dibo.ltd).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.diboot.core.binding;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.core.conditions.ISqlSegment;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.segments.NormalSegmentList;
import com.diboot.core.binding.parser.ParserCache;
import com.diboot.core.binding.query.BindQuery;
import com.diboot.core.binding.query.Comparison;
import com.diboot.core.binding.query.Strategy;
import com.diboot.core.binding.query.dynamic.AnnoJoiner;
import com.diboot.core.binding.query.dynamic.DynamicJoinQueryWrapper;
import com.diboot.core.binding.query.dynamic.ExtQueryWrapper;
import com.diboot.core.config.Cons;
import com.diboot.core.data.ProtectFieldHandler;
import com.diboot.core.util.BeanUtils;
import com.diboot.core.util.ContextHelper;
import com.diboot.core.util.S;
import com.diboot.core.util.V;
import com.diboot.core.vo.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.type.NullType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * QueryWrapper?????????
 * @author mazc@dibo.ltd
 * @version v2.0
 * @date 2019/07/27
 */
@SuppressWarnings({"unchecked", "rawtypes", "JavaDoc"})
public class QueryBuilder {
    private static Logger log = LoggerFactory.getLogger(QueryBuilder.class);

    /**
     * Entity??????DTO???????????????QueryWrapper
     * @param dto
     * @param <DTO>
     * @return
     */
    public static <DTO> QueryWrapper toQueryWrapper(DTO dto){
        return dtoToWrapper(dto, null, null);
    }

    /**
     * Entity??????DTO???????????????QueryWrapper
     * @param dto
     * @param pagination ??????
     * @param <DTO>
     * @return
     */
    public static <DTO> QueryWrapper toQueryWrapper(DTO dto, Pagination pagination){
        return dtoToWrapper(dto, null, pagination);
    }

    /**
     * Entity??????DTO???????????????QueryWrapper
     * @param dto
     * @param fields ??????????????????????????????
     * @param <DTO>
     * @return
     */
    public static <DTO> QueryWrapper toQueryWrapper(DTO dto, Collection<String> fields){
        return dtoToWrapper(dto, fields, null);
    }

    /**
     * Entity??????DTO???????????????QueryWrapper
     * @param dto
     * @param fields ??????????????????????????????
     * @param pagination ??????
     * @param <DTO>
     * @return
     */
    public static <DTO> QueryWrapper toQueryWrapper(DTO dto, Collection<String> fields, Pagination pagination){
        return dtoToWrapper(dto, fields, pagination);
    }

    /**
     * Entity??????DTO???????????????QueryWrapper
     * @param dto
     * @param <DTO>
     * @return
     */
    public static <DTO> ExtQueryWrapper toDynamicJoinQueryWrapper(DTO dto){
        return toDynamicJoinQueryWrapper(dto, null, null);
    }

    /**
     * Entity??????DTO???????????????QueryWrapper
     * @param dto
     * @param pagination ??????
     * @param <DTO>
     * @return
     */
    public static <DTO> ExtQueryWrapper toDynamicJoinQueryWrapper(DTO dto, Pagination pagination){
        return toDynamicJoinQueryWrapper(dto, null, pagination);
    }

    /**
     * Entity??????DTO???????????????QueryWrapper
     * @param dto
     * @param <DTO>
     * @return
     */
    public static <DTO> ExtQueryWrapper toDynamicJoinQueryWrapper(DTO dto, Collection<String> fields){
        return toDynamicJoinQueryWrapper(dto, fields, null);
    }

    /**
     * Entity??????DTO???????????????QueryWrapper
     * @param dto
     * @param fields ??????????????????????????????
     * @param <DTO>
     * @return
     */
    public static <DTO> ExtQueryWrapper toDynamicJoinQueryWrapper(DTO dto, Collection<String> fields, Pagination pagination){
        QueryWrapper queryWrapper = dtoToWrapper(dto, fields, pagination);
        if(!(queryWrapper instanceof DynamicJoinQueryWrapper)){
            return (ExtQueryWrapper)queryWrapper;
        }
        return (DynamicJoinQueryWrapper)queryWrapper;
    }

    /**
     * ??????????????????
     *
     * @param dto
     * @return
     */
    private static <DTO> QueryWrapper<?> dtoToWrapper(DTO dto, Collection<String> fields, Pagination pagination) {
        QueryWrapper<?> wrapper;
        // ??????
        LinkedHashMap<String, FieldAndValue> fieldValuesMap = extractNotNullValues(dto, fields, pagination);
        if (V.isEmpty(fieldValuesMap)) {
            return new QueryWrapper<>();
        }
        // ??????????????????
        fields = fieldValuesMap.keySet();
        // ?????????join????????????
        boolean hasJoinTable = ParserCache.hasJoinTable(dto, fields);
        if (hasJoinTable) {
            wrapper = new DynamicJoinQueryWrapper<>(dto.getClass(), fields);
        } else {
            wrapper = new ExtQueryWrapper<>();
        }
        // ?????? ColumnName
        List<AnnoJoiner> annoJoinerList = ParserCache.getBindQueryAnnos(dto.getClass());
        BiFunction<BindQuery, Field, String> buildColumnName = (bindQuery, field) -> {
            if (bindQuery != null) {
                String key = field.getName() + bindQuery;
                for (AnnoJoiner annoJoiner : annoJoinerList) {
                    if (key.equals(annoJoiner.getKey())) {
                        if (V.notEmpty(annoJoiner.getJoin())) {
                            // ????????????Table
                            return annoJoiner.getAlias() + "." + annoJoiner.getColumnName();
                        } else {
                            return (hasJoinTable ? "self." : "") + annoJoiner.getColumnName();
                        }
                    }
                }
            }
            return (hasJoinTable ? "self." : "") + BeanUtils.getColumnName(field);
        };
        // ??????????????????"",????????????
        BiPredicate<Object, BindQuery> ignoreEmpty = (value, bindQuery) -> bindQuery != null &&
                (Strategy.IGNORE_EMPTY.equals(bindQuery.strategy()) && value instanceof String && S.isEmpty((String) value) // ??????????????????""
                        || Comparison.IN.equals(bindQuery.comparison()) && V.isEmpty(value)); // ???????????????
        // ??????Class??????
        Function<BindQuery, Class<?>> getClass = bindQuery -> bindQuery == null || bindQuery.entity() == NullType.class ? dto.getClass() : bindQuery.entity();
        // ?????????????????????
        BiFunction<BindQuery, String, String> getFieldName = (bindQuery, defFieldName) -> bindQuery == null || S.isEmpty(bindQuery.field()) ? defFieldName : bindQuery.field();
        // ?????????????????????
        ProtectFieldHandler protectFieldHandler = ContextHelper.getBean(ProtectFieldHandler.class);
        // ??????QueryWrapper
        for (Map.Entry<String, FieldAndValue> entry : fieldValuesMap.entrySet()) {
            FieldAndValue fieldAndValue = entry.getValue();
            Field field = fieldAndValue.getField();
            //???????????? @TableField(exist = false) ?????????
            TableField tableField = field.getAnnotation(TableField.class);
            if (tableField != null && !tableField.exist()) {
                continue;
            }
            //????????????
            BindQuery query = field.getAnnotation(BindQuery.class);
            if (query != null && query.ignore()) {
                continue;
            }
            BindQuery.List queryList = field.getAnnotation(BindQuery.List.class);
            Object value = fieldAndValue.getValue();
            // ??????Query
            if (queryList != null) {
                List<BindQuery> bindQueryList = Arrays.stream(queryList.value()).filter(e -> !ignoreEmpty.test(value, e)).collect(Collectors.toList());
                wrapper.and(V.notEmpty(bindQueryList), queryWrapper -> {
                    for (BindQuery bindQuery : bindQueryList) {
                        String columnName = buildColumnName.apply(bindQuery, field);
                        if (protectFieldHandler != null) {
                            Class<?> clazz = getClass.apply(query);
                            String fieldName = getFieldName.apply(query, entry.getKey());
                            if (ParserCache.getProtectFieldList(clazz).contains(fieldName)) {
                                buildQuery(queryWrapper.or(), bindQuery, columnName, protectFieldHandler.encrypt(clazz, fieldName, value.toString()));
                                continue;
                            }
                        }
                        buildQuery(queryWrapper.or(), bindQuery, columnName, value);
                    }
                });
            } else {
                if(query == null && V.isEmpty(value)) {
                    continue;
                }
                if (ignoreEmpty.test(value, query)) {
                    continue;
                }
                String columnName = buildColumnName.apply(query, field);
                if (protectFieldHandler != null){
                    Class<?> clazz = getClass.apply(query);
                    String fieldName = getFieldName.apply(query, entry.getKey());
                    if (ParserCache.getProtectFieldList(clazz).contains(fieldName)) {
                        buildQuery(wrapper, query, columnName, protectFieldHandler.encrypt(clazz, fieldName, value.toString()));
                        continue;
                    }
                }
                buildQuery(wrapper, query, columnName, value);
            }
        }
        return wrapper;
    }

    /**
     * ????????????
     *
     * @param wrapper    ???????????????
     * @param bindQuery ??????
     * @param columnName ??????
     * @param value      ???
     */
    private static void buildQuery(QueryWrapper<?> wrapper, BindQuery bindQuery, String columnName, Object value) {
        Comparison comparison = bindQuery != null ? bindQuery.comparison() : Comparison.EQ;
        if(value == null) {
            if(bindQuery != null && bindQuery.strategy().equals(Strategy.INCLUDE_NULL) && comparison.equals(Comparison.EQ)) {
                wrapper.isNull(columnName);
            }
            return;
        }
        switch (comparison) {
            case EQ:
                wrapper.eq(columnName, value);
                break;
            case IN:
                if (value.getClass().isArray()) {
                    Object[] valueArray = (Object[]) value;
                    if (valueArray.length == 1) {
                        wrapper.eq(columnName, valueArray[0]);
                    } else if (valueArray.length >= 2) {
                        wrapper.in(columnName, valueArray);
                    }
                } else if (value instanceof Collection) {
                    wrapper.in(!((Collection) value).isEmpty(), columnName, (Collection<?>) value);
                } else {
                    log.warn("?????????????????????IN?????????List?????????.");
                }
                break;
            case NOT_IN:
                if (value.getClass().isArray()) {
                    Object[] valueArray = (Object[]) value;
                    if (valueArray.length == 1) {
                        wrapper.ne(columnName, valueArray[0]);
                    } else if (valueArray.length >= 2) {
                        wrapper.notIn(columnName, valueArray);
                    }
                } else if (value instanceof Collection) {
                    wrapper.notIn(!((Collection) value).isEmpty(), columnName, (Collection<?>) value);
                } else {
                    log.warn("?????????????????????NOT_IN?????????List?????????.");
                }
                break;
            case CONTAINS:
            case LIKE:
                wrapper.like(columnName, value);
                break;
            case STARTSWITH:
                wrapper.likeRight(columnName, value);
                break;
            case ENDSWITH:
                wrapper.likeLeft(columnName, value);
                break;
            case GT:
                wrapper.gt(columnName, value);
                break;
            case BETWEEN_BEGIN:
            case GE:
                wrapper.ge(columnName, value);
                break;
            case LT:
                wrapper.lt(columnName, value);
                break;
            case BETWEEN_END:
            case LE:
                wrapper.le(columnName, value);
                break;
            case BETWEEN:
                if (value.getClass().isArray()) {
                    Object[] valueArray = (Object[]) value;
                    if (valueArray.length == 1) {
                        wrapper.ge(columnName, valueArray[0]);
                    } else if (valueArray.length >= 2) {
                        wrapper.between(columnName, valueArray[0], valueArray[1]);
                    }
                } else if (value instanceof List) {
                    List<?> valueList = (List<?>) value;
                    if (valueList.size() == 1) {
                        wrapper.ge(columnName, valueList.get(0));
                    } else if (valueList.size() >= 2) {
                        wrapper.between(columnName, valueList.get(0), valueList.get(1));
                    }
                }
                // ??????????????????????????????
                else if (value instanceof String && ((String) value).contains(Cons.SEPARATOR_COMMA)) {
                    Object[] valueArray = ((String) value).split(Cons.SEPARATOR_COMMA);
                    wrapper.between(columnName, valueArray[0], valueArray[1]);
                } else {
                    wrapper.ge(columnName, value);
                }
                break;
            // ?????????
            case NOT_EQ:
                wrapper.ne(columnName, value);
                break;
            default:
                break;
        }
    }

    /**
     * ????????????????????????
     * @param dto
     * @param fields
     * @param <DTO>
     * @return
     */
    private static <DTO> LinkedHashMap<String, FieldAndValue> extractNotNullValues(DTO dto, Collection<String> fields, Pagination pagination){
        Class<?> dtoClass = dto.getClass();
        // ??????
        List<Field> declaredFields = BeanUtils.extractAllFields(dtoClass);
        List<String> extractOrderFieldNames = extractOrderFieldNames(pagination);
        // ??????map???<?????????,??????????????????>
        LinkedHashMap<String, FieldAndValue> resultMap = new LinkedHashMap<>(declaredFields.size());
        for (Field field : declaredFields) {
            String fieldName = field.getName();
            // ???????????????????????????????????????????????????
            if (V.notContains(fields, fieldName)) {
                //Date ????????????
                if (!V.equals(field.getType(), Date.class)) {
                    continue;
                }
            }
            //??????static?????????final???transient
            int modifiers = field.getModifiers();
            boolean isStatic = Modifier.isStatic(modifiers);
            boolean isFinal = Modifier.isFinal(modifiers);
            boolean isTransient = Modifier.isTransient(modifiers);
            if (isStatic || isFinal || isTransient) {
                continue;
            }
            //?????????????????? ?????????
            field.setAccessible(true);
            Object value = null;
            try {
                value = field.get(dto);
                if (V.isEmpty(value)) {
                    String prefix = V.equals(boolean.class, field.getType()) ?  "is" : "get";
                    Method method = dtoClass.getMethod(prefix + S.capFirst(fieldName));
                    value = method.invoke(dto);
                }
            } catch (IllegalAccessException e) {
                log.error("????????????????????????????????????{}", e.getMessage());
            } catch (NoSuchMethodException e) {
                log.debug("??????????????????????????????????????????{}", e.getMessage());
            } catch (InvocationTargetException e) {
                log.warn("???????????????????????????????????????{}", e.getMessage());
            }
            // ??????????????????????????????????????????????????????????????????false????????????
            if (field.isAnnotationPresent(TableLogic.class) && V.equals(false, value)) {
                continue;
            }
            BindQuery bindQuery = field.getAnnotation(BindQuery.class);
            Strategy strategy = bindQuery != null? bindQuery.strategy() : Strategy.IGNORE_EMPTY;
            boolean collectThisField = false;
            // INCLUDE_NULL???????????????null?????????
            if(strategy.equals(Strategy.INCLUDE_NULL)) {
                collectThisField = true;
            }
            else if(strategy.equals(Strategy.IGNORE_EMPTY) && V.notEmpty(value)) {
                collectThisField = true;
            }
            else if(strategy.equals(Strategy.INCLUDE_EMPTY) && value != null) {
                collectThisField = true;
            }
            else if(extractOrderFieldNames.contains(fieldName)) {
                collectThisField = true;
            }
            if (collectThisField) {
                resultMap.put(fieldName, new FieldAndValue(field, value));
            }
        }
        return resultMap;
    }

    /**
     * ????????????Field??????????????????
     */
    private static class FieldAndValue {
        private final Field field;
        private final Object value;

        public FieldAndValue(Field field, Object value) {
            this.field = field;
            this.value = value;
        }

        public Field getField() {
            return field;
        }

        public Object getValue() {
            return value;
        }
    }

    /**
     * ?????????????????????
     * @param segments
     * @param idCol
     * @return
     */
    public static boolean checkHasColumn(NormalSegmentList segments, String idCol){
        if(segments.size() > 0){
            for (ISqlSegment segment : segments) {
                if(segment.getSqlSegment().equalsIgnoreCase(idCol)){
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * ?????????????????????
     * @param pagination
     * @return
     */
    private static List<String> extractOrderFieldNames(Pagination pagination) {
        if (pagination == null || V.isEmpty(pagination.getOrderBy())) {
            return Collections.emptyList();
        }
        // ????????????
        // orderBy=shortName:DESC,age:ASC,birthdate
        String[] orderByFields = S.split(pagination.getOrderBy());
        List<String> orderFields = new ArrayList<>(orderByFields.length);
        for (String field : orderByFields) {
            if (field.contains(":")) {
                field = S.substringBefore(field, ":");
            }
            orderFields.add(field);
        }
        return orderFields;
    }

}
