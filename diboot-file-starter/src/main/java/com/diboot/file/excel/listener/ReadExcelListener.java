/*
 * Copyright (c) 2015-2021, www.dibo.ltd (service@dibo.ltd).
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
package com.diboot.file.excel.listener;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.exception.ExcelDataConvertException;
import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.read.listener.ModelBuildEventListener;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.read.metadata.property.ExcelReadHeadProperty;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.diboot.core.binding.annotation.BindDict;
import com.diboot.core.config.BaseConfig;
import com.diboot.core.exception.BusinessException;
import com.diboot.core.util.BeanUtils;
import com.diboot.core.util.S;
import com.diboot.core.util.V;
import com.diboot.core.vo.Status;
import com.diboot.file.config.Cons;
import com.diboot.file.excel.BaseExcelModel;
import com.diboot.file.excel.annotation.DuplicateStrategy;
import com.diboot.file.excel.annotation.EmptyStrategy;
import com.diboot.file.excel.annotation.ExcelBindDict;
import com.diboot.file.excel.annotation.ExcelBindField;
import com.diboot.file.excel.cache.ExcelBindAnnoHandler;
import com.diboot.file.excel.write.CommentWriteHandler;
import com.diboot.file.util.ExcelHelper;
import com.diboot.file.util.FileHelper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.validation.ConstraintViolation;
import javax.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * ??????Excel
 *
 * @author wind
 * @version v2.4.0
 * @date 2021/11/22
 */
@Slf4j
public abstract class ReadExcelListener<T extends BaseExcelModel> implements ReadListener<T> {

    /**
     * ????????????excel??????
     */
    @Getter
    @Deprecated
    protected Map<Integer, String> headMap = new HashMap<>();
    /**
     * ?????????-???????????????
     */
    @Getter
    private final Map<String, String> fieldHeadMap = new HashMap<>();
    /**
     * ???????????????
     */
    @Getter
    private final HashMap<Integer, String> fieldNameMap = new HashMap<>();
    /**
     * ????????????
     */
    private final TreeMap<Integer, List<String>> headNameMap = new TreeMap<>();
    /**
     * ??????request
     */
    @Setter
    private Map<String, Object> requestParams;
    /**
     * ?????????????????????
     */
    @Setter
    protected boolean preview = false;
    /**
     * ???????????????uuid
     */
    @Setter
    protected String uploadFileUuid;

    /**
     * ????????????
     */
    @Getter
    private List<T> previewDataList;

    /**
     * ????????????
     */
    @Getter
    private List<String> exceptionMsgs = null;

    /**
     * ????????????
     */
    @Getter
    private List<String> errorMsgs;

    @Getter
    private Integer totalCount = 0;
    @Getter
    protected Integer errorCount = 0;

    /**
     * <h3>??????????????????</h3>
     * ??????excel?????????
     */
    @Getter
    private String errorDataFilePath;
    private ExcelWriter excelWriter;
    private WriteSheet writeSheet;
    private CommentWriteHandler commentWriteHandler;

    /**
     * ????????????????????????
     */
    public Integer getProperCount() {
        return totalCount - errorCount;
    }

    /**
     * excel????????????
     **/
    @Override
    public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context) {
        this.headMap.clear();
        fieldHeadMap.clear();
        fieldNameMap.clear();
        headNameMap.clear();
        ExcelReadHeadProperty excelReadHeadProperty = context.currentReadHolder().excelReadHeadProperty();
        for (Map.Entry<Integer, Head> entry : excelReadHeadProperty.getHeadMap().entrySet()) {
            Integer index = entry.getKey();
            Head head = entry.getValue();
            String fieldName = head.getFieldName();
            List<String> headNameList = head.getHeadNameList();
            String name = headNameList.get(headNameList.size() - 1);
            this.headMap.put(index, name);
            fieldHeadMap.put(fieldName, name);
            fieldNameMap.put(index, fieldName);
            headNameMap.put(index, headNameList);
        }
    }

    /**
     * ??????????????????????????????????????????
     */
    @Override
    public void invoke(T data, AnalysisContext context) {
        // ????????????
        data.setRowIndex(context.readRowHolder().getRowIndex());
        // ????????????
        Set<ConstraintViolation<T>> constraintViolations = V.validateBean(data);
        if (V.notEmpty(constraintViolations)) {
            for (ConstraintViolation<T> violation : constraintViolations) {
                data.addComment(violation.getPropertyPath().toString(), violation.getMessage());
            }
        }
        this.cachedData(data);
    }

    /**
     * <h3>??????</h3>
     * ??????????????????????????????
     */
    protected void finish() {
        if (excelWriter != null) {
            excelWriter.finish();
        }
        // ????????? ????????????
        if (V.notEmpty(this.exceptionMsgs)) {
            throw new BusinessException(Status.FAIL_VALIDATION, S.join(this.exceptionMsgs, "; "));
        }
    }

    /**
     * <h3>????????????</h3>
     * ???????????????????????????
     */
    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        //????????????????????????
        if (exception instanceof ExcelDataConvertException) {
            ExcelDataConvertException dataConvertException = (ExcelDataConvertException) exception;

            Map<Integer, ReadCellData<?>> cellMap = new HashMap<>((Map) context.readRowHolder().getCellMap());
            Map<String, String> errorDataMap = new HashMap<>();
            Map<String, String> errorMsgMap = new HashMap<>();

            Consumer<ExcelDataConvertException> addErrorData = e -> {
                Integer columnIndex = e.getColumnIndex();
                String key = fieldNameMap.get(columnIndex);
                errorDataMap.put(key, cellMap.remove(columnIndex).getStringValue());
                errorMsgMap.put(key, "???????????????????????????????????????????????????[" + e.getExcelContentProperty().getField().getType().getSimpleName() + "]");
            };
            addErrorData.accept(dataConvertException);

            ReadListener<?> readListener = context.readWorkbookHolder().getReadListenerList().get(0);
            if (readListener instanceof ModelBuildEventListener) {
                while (true) {
                    try {
                        ((ModelBuildEventListener) readListener).invoke(cellMap, context);
                        break;
                    } catch (ExcelDataConvertException convertException) {
                        addErrorData.accept(convertException);
                    }
                }
            } else {
                log.error("??????????????????", exception);
                StringBuilder errorMsg = new StringBuilder().append("??? ").append(context.readRowHolder().getRowIndex() + 1).append(" ??????");
                errorMsgMap.forEach((fieldName, msg) -> errorMsg.append(fieldHeadMap.get(fieldName)).append("???").append(msg));
                addExceptionMsg(errorMsg.toString());
                return;
            }
            T currentRowAnalysisResult = (T) context.readRowHolder().getCurrentRowAnalysisResult();
            currentRowAnalysisResult.setRowIndex(context.readRowHolder().getRowIndex());
            errorDataMap.forEach(currentRowAnalysisResult::addInvalidValue);
            errorMsgMap.forEach(currentRowAnalysisResult::addComment);
            // ????????????
            Set<ConstraintViolation<T>> constraintViolations = V.validateBean(currentRowAnalysisResult);
            if (V.notEmpty(constraintViolations)) {
                for (ConstraintViolation<T> violation : constraintViolations) {
                    String propertyName = violation.getPropertyPath().toString();
                    // ?????????????????????????????????
                    if (!errorDataMap.containsKey(propertyName)) {
                        currentRowAnalysisResult.addComment(propertyName, violation.getMessage());
                    }
                }
            }
            this.cachedData(currentRowAnalysisResult);
        } else {
            log.error("???????????????????????????", exception);
            addExceptionMsg("??? " + (context.readRowHolder().getRowIndex() + 1) + " ??????????????????: " + exception.getMessage());
        }
    }

    /**
     * <h3>????????????</h3>
     */
    protected abstract void cachedData(T data);

    /**
     * ??????????????????
     *
     * @param exceptionMsg
     */
    protected void addExceptionMsg(String exceptionMsg) {
        if (this.exceptionMsgs == null) {
            this.exceptionMsgs = new ArrayList<>();
        }
        this.exceptionMsgs.add(exceptionMsg);
    }

    /**
     * <h3>????????????</h3>
     *
     * @param dataList ????????????
     */
    protected void handle(List<T> dataList) {
        if (preview && previewDataList == null) {
            int pageSize = BaseConfig.getPageSize();
            previewDataList = dataList.size() > pageSize ? dataList.subList(0, pageSize) : dataList;
        }
        totalCount += dataList.size();
        // ?????? ?????????????????????
        validateOrConvertDictAndRefField(dataList, true);
        // ???????????????
        additionalValidate(dataList, requestParams);
        dataList.stream().collect(Collectors.groupingBy(this::isProper)).forEach((proper, list) -> {
            if (proper) {
                if (!preview) {
                    // ?????? ?????????????????????
                    validateOrConvertDictAndRefField(list, false);
                    this.saveData(list, requestParams);
                }
            } else {
                this.errorData(list);
            }
        });
    }

    /**
     * <h3>?????????????????????</h3>
     * ???????????????
     *
     * @param data ??????
     * @return
     */
    protected boolean isProper(T data) {
        return V.isEmpty(data.getComment());
    }

    /**
     * <h3>??????????????????</h3>
     *
     * @param dataList
     */
    protected void errorData(List<T> dataList) {
        errorCount += dataList.size();
        if (errorMsgs == null || errorMsgs.size() < BaseConfig.getPageSize()) {
            if (errorMsgs == null) {
                errorMsgs = new ArrayList<>();
            }
            StringBuilder builder = new StringBuilder();
            dataList.stream().limit(BaseConfig.getPageSize() - errorMsgs.size()).map(data -> {
                builder.setLength(0);
                builder.append("??? ").append(data.getRowIndex() + 1).append(" ??????");
                data.getComment().forEach((k, v) -> {
                    builder.append(fieldHeadMap.get(k)).append("???")
                            .append(S.getIfEmpty(data.getField2InvalidValueMap().get(k), () -> S.defaultValueOf(BeanUtils.getProperty(data, k)))).append(" ")
                            .append(S.join(v)).append("???");
                });
                return builder.toString();
            }).forEach(errorMsgs::add);
        }
        if (preview) {
            return;
        }
        if (excelWriter == null) {
            if (FileHelper.isLocalStorage()) {
                errorDataFilePath = FileHelper.getFullPath(S.newUuid() + ".xlsx");
            } else {
                errorDataFilePath = FileHelper.getSystemTempDir() + BaseConfig.getProperty("spring.application.name", "diboot")
                        + Cons.FILE_PATH_SEPARATOR + S.newUuid() + ".xlsx";
            }
            FileHelper.makeDirectory(errorDataFilePath);
            excelWriter = EasyExcel.write(errorDataFilePath, getExcelModelClass()).build();
            ExcelHelper.buildWriteSheet(null, (commentWriteHandler, writeSheet) -> {
                this.commentWriteHandler = commentWriteHandler;
                this.writeSheet = writeSheet;
            });
        }
        commentWriteHandler.setDataList(dataList);
        excelWriter.write(dataList, writeSheet);
    }

    /**
     * ????????????????????????????????????
     */
    protected void validateOrConvertDictAndRefField(List<T> dataList, boolean preview) {
        Class<T> tClass = getExcelModelClass();
        Map<String, Annotation> fieldName2BindAnnoMap = ExcelBindAnnoHandler.getField2BindAnnoMap(tClass);
        if (fieldName2BindAnnoMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Annotation> entry : fieldName2BindAnnoMap.entrySet()) {
            List nameList = (entry.getValue() instanceof ExcelBindField) ? BeanUtils.collectToList(dataList, entry.getKey()) : null;
            Map<String, List> map = ExcelBindAnnoHandler.convertToNameValueMap(entry.getValue(), nameList);
            Field field = BeanUtils.extractField(tClass, entry.getKey());
            boolean valueNotNull = (field.getAnnotation(NotNull.class) != null);
            for (T data : dataList) {
                String name = BeanUtils.getStringProperty(data, entry.getKey());
                if (S.isEmpty(name)) {
                    continue;
                }
                List valList = map.get(name);
                if (entry.getValue() instanceof ExcelBindField) {
                    ExcelBindField excelBindField = (ExcelBindField) entry.getValue();
                    String setFieldName = S.defaultIfEmpty(excelBindField.setIdField(), entry.getKey());
                    if (V.isEmpty(valList)) {
                        if (excelBindField.empty().equals(EmptyStrategy.SET_0)) {
                            // ???????????? ??????
                            if (!preview) {
                                BeanUtils.setProperty(data, setFieldName, 0);
                            }
                        } else if (excelBindField.empty().equals(EmptyStrategy.WARN)) {
                            data.addComment(entry.getKey(), "??????????????????");
                        } else if (excelBindField.empty().equals(EmptyStrategy.IGNORE) && valueNotNull) {
                            log.warn("???????????? {} ???????????? EmptyStrategy.IGNORE.", entry.getKey());
                        }
                    } else if (valList.size() == 1) {
                        // ???????????? ??????
                        if (!preview) {
                            BeanUtils.setProperty(data, setFieldName, valList.get(0));
                        }
                    } else {
                        if (excelBindField.duplicate().equals(DuplicateStrategy.WARN)) {
                            data.addComment(entry.getKey(), "????????????????????????");
                        } else if (excelBindField.duplicate().equals(DuplicateStrategy.FIRST)) {
                            // ???????????? ??????
                            if (!preview) {
                                BeanUtils.setProperty(data, setFieldName, valList.get(0));
                            }
                        }
                    }
                } else if (entry.getValue() instanceof ExcelBindDict || entry.getValue() instanceof BindDict) {
                    if (V.isEmpty(valList)) {
                        if (name.contains(S.SEPARATOR)) {
                            valList = new LinkedList<>();
                            for (String item : name.split(S.SEPARATOR)) {
                                valList.addAll(map.get(item));
                            }
                            if (valList.size() > 0) {
                                valList.add(0, S.join(valList));
                            }
                        }
                        // ???????????????
                        if (valueNotNull && V.isEmpty(valList)) {
                            data.addComment(entry.getKey(), "???????????????");
                            continue;
                        }
                    }
                    // ???????????? ??????
                    if (!preview && V.notEmpty(valList)) {
                        BeanUtils.setProperty(data, entry.getKey(), valList.get(0));
                    }
                }
            }
        }
    }

    /**
     * <h3>???????????????????????????</h3>
     * ?????????????????????????????????????????????????????????
     */
    protected abstract void additionalValidate(List<T> dataList, Map<String, Object> requestParams);

    /**
     * <h3>????????????</h3>
     */
    protected abstract void saveData(List<T> dataList, Map<String, Object> requestParams);

    /**
     * ??????Excel??????
     *
     * @return ????????????
     */
    public List<ExcelHelper.TableHead> getTableHead() {
        return ExcelHelper.buildTableHead(headNameMap, fieldNameMap);
    }

    /**
     * ??????Excel?????????Model???
     */
    public Class<T> getExcelModelClass() {
        return BeanUtils.getGenericityClass(this, 0);
    }

}
