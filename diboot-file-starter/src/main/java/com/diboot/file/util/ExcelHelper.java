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
package com.diboot.file.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.util.ClassUtils;
import com.alibaba.excel.util.FieldUtils;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.builder.ExcelWriterSheetBuilder;
import com.alibaba.excel.write.handler.WriteHandler;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.diboot.core.exception.BusinessException;
import com.diboot.core.util.BeanUtils;
import com.diboot.core.util.S;
import com.diboot.core.util.V;
import com.diboot.core.vo.Status;
import com.diboot.file.excel.BaseExcelModel;
import com.diboot.file.excel.listener.DynamicHeadExcelListener;
import com.diboot.file.excel.listener.FixedHeadExcelListener;
import com.diboot.file.excel.write.CommentWriteHandler;
import com.diboot.file.excel.write.OptionWriteHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/***
 * excel???????????????????????????
 * @auther wangyl@dibo.ltd
 * @date 2019-10-9
 */
@Slf4j
public class ExcelHelper {

    /**
     * ??????ecxel
     *
     * @param inputStream
     * @param excelType   excel???????????????????????????
     * @param listener
     */
    public static void read(InputStream inputStream, ExcelTypeEnum excelType, ReadListener<?> listener) {
        read(inputStream, excelType, listener, null);
    }

    /**
     * ??????ecxel
     *
     * @param inputStream
     * @param excelType   excel???????????????????????????
     * @param listener
     * @param headClazz   ExcelModel.class
     */
    public static <T> void read(InputStream inputStream, ExcelTypeEnum excelType, ReadListener<T> listener, Class<T> headClazz) {
        EasyExcel.read(inputStream).excelType(excelType).registerReadListener(listener).head(headClazz).sheet().doRead();
    }

    /**
     * ????????????excel????????????
     *
     * @param filePath
     * @param listener
     * @return
     */
    @Deprecated
    public static <T extends BaseExcelModel> boolean previewReadExcel(String filePath, FixedHeadExcelListener listener) throws Exception {
        listener.setPreview(true);
        return readAndSaveExcel(filePath, listener);
    }

    /**
     * ????????????excel????????????
     *
     * @param inputStream
     * @param listener
     * @return
     */
    @Deprecated
    public static <T extends BaseExcelModel> boolean previewReadExcel(InputStream inputStream, FixedHeadExcelListener listener) throws Exception {
        listener.setPreview(true);
        return readAndSaveExcel(inputStream, listener);
    }

    /**
     * ??????excel?????????????????????????????????
     *
     * @param filePath
     * @param listener
     * @return
     */
    @Deprecated
    public static <T extends BaseExcelModel> boolean readAndSaveExcel(String filePath, FixedHeadExcelListener listener) throws Exception {
        File excel = getExcelFile(filePath);
        Class<T> headClazz = BeanUtils.getGenericityClass(listener, 0);
        EasyExcel.read(excel).registerReadListener(listener).head(headClazz).sheet().doRead();
        return true;
    }

    /**
     * ??????excel????????????????????????????????????
     *
     * @param listener
     * @return
     */
    @Deprecated
    public static <T extends BaseExcelModel> boolean readAndSaveExcel(InputStream inputStream, FixedHeadExcelListener listener) throws Exception {
        Class<T> headClazz = BeanUtils.getGenericityClass(listener, 0);
        EasyExcel.read(inputStream).registerReadListener(listener).head(headClazz).sheet().doRead();
        return true;
    }

    /**
     * ???????????????/???????????????excel????????????
     *
     * @param filePath
     * @return
     */
    @Deprecated
    public static boolean readDynamicHeadExcel(String filePath, DynamicHeadExcelListener listener) {
        File excel = getExcelFile(filePath);
        EasyExcel.read(excel).registerReadListener(listener).sheet().doRead();
        return true;
    }

    /**
     * ???????????????/???????????????excel????????????
     *
     * @param inputStream
     * @param listener
     * @return
     */
    @Deprecated
    public static boolean readDynamicHeadExcel(InputStream inputStream, DynamicHeadExcelListener listener) {
        EasyExcel.read(inputStream).registerReadListener(listener).sheet().doRead();
        return true;
    }

    /**
     * ?????????????????????excel??????
     * <p>?????????????????????????????????, ????????????</p>
     *
     * @param filePath
     * @param sheetName
     * @param dataList
     * @param writeHandlers
     * @return
     */
    @Deprecated
    public static boolean writeDynamicData(String filePath, String sheetName, List<List<String>> dataList,
                                           WriteHandler... writeHandlers) throws Exception {
        try {
            ExcelWriterBuilder write = EasyExcel.write(filePath);
            write = write.registerWriteHandler(new LongestMatchColumnWidthStyleStrategy());
            for (WriteHandler handler : writeHandlers) {
                write = write.registerWriteHandler(handler);
            }
            ExcelWriterSheetBuilder sheet = write.sheet(sheetName);
            sheet.doWrite(dataList);
            return true;
        } catch (Exception e) {
            log.error("????????????excel????????????", e);
            return false;
        }
    }

    /**
     * ?????????????????????excel??????
     * <p>???????????????????????????????????????????????????????????????, ????????????</p>
     *
     * @param filePath
     * @param sheetName
     * @param dataList
     * @param <T>
     * @return
     */
    @Deprecated
    public static <T extends BaseExcelModel> boolean writeData(String filePath, String sheetName, List<T> dataList,
                                                               WriteHandler... writeHandlers) throws Exception {
        try {
            if (V.isEmpty(dataList)) {
                return writeDynamicData(filePath, sheetName, Collections.emptyList());
            }
            Class<T> tClass = (Class<T>) dataList.get(0).getClass();
            ExcelWriterBuilder write = EasyExcel.write(filePath, tClass);
            write = write.registerWriteHandler(new LongestMatchColumnWidthStyleStrategy());
            for (WriteHandler handler : writeHandlers) {
                write = write.registerWriteHandler(handler);
            }
            write.sheet(sheetName).doWrite(dataList);
            return true;
        } catch (Exception e) {
            log.error("????????????excel????????????", e);
            return false;
        }
    }

    /**
     * Excel??????
     * <p>
     * ?????????????????????????????????????????????????????????
     *
     * @param outputStream
     * @param clazz         ?????????ExcelModel
     * @param dataList
     * @param writeHandlers ??????????????????
     * @return
     */
    public static <T> boolean write(OutputStream outputStream, Class<T> clazz, List<T> dataList, WriteHandler... writeHandlers) {
        return write(outputStream, clazz, null, dataList, writeHandlers);
    }

    /**
     * Excel??????
     *
     * @param outputStream
     * @param clazz          ?????????ExcelModel
     * @param columnNameList ??????????????????????????????????????????????????????
     * @param dataList
     * @param writeHandlers  ??????????????????
     * @return
     */
    public static <T> boolean write(OutputStream outputStream, Class<T> clazz, Collection<String> columnNameList,
                                    List<T> dataList, WriteHandler... writeHandlers) {
        AtomicBoolean first = new AtomicBoolean(true);
        return write(outputStream, clazz, columnNameList, () -> first.getAndSet(false) ? dataList : null, writeHandlers);
    }

    /**
     * ??????????????????
     *
     * @param outputStream
     * @param clazz
     * @param columnNameList
     * @param dataList
     * @param writeHandlers
     * @return
     */
    public static <T> boolean write(OutputStream outputStream, Class<T> clazz, Collection<String> columnNameList,
                                    Supplier<List<T>> dataList, WriteHandler... writeHandlers) {
        try {
            write(outputStream, clazz, columnNameList, null, dataList, writeHandlers);
            return true;
        } catch (Exception e) {
            log.error("????????????excel????????????", e);
            return false;
        }
    }

    /**
     * ????????????
     *
     * @param outputStream
     * @param clazz
     * @param columnNameList
     * @param autoClose      ?????????????????????
     * @param dataList
     * @param writeHandlers
     */
    public static <T> void write(OutputStream outputStream, Class<T> clazz, Collection<String> columnNameList,
                                 Boolean autoClose, Supplier<List<T>> dataList, WriteHandler... writeHandlers) {
        ExcelWriter writer = EasyExcel.write(outputStream, clazz).autoCloseStream(autoClose).build();
        buildWriteSheet(columnNameList, (commentWriteHandler, writeSheet) -> {
            List<T> list = dataList.get();
            boolean assignableFrom = BaseExcelModel.class.isAssignableFrom(clazz);
            do {
                if (assignableFrom) {
                    commentWriteHandler.setDataList((List<? extends BaseExcelModel>) list);
                }
                writer.write(list, writeSheet);
            } while (V.notEmpty(list = dataList.get()));
        }, writeHandlers);
        writer.finish();
    }

    /**
     * ??????WriteSheet
     * <p>
     * ?????????????????????????????????????????????????????????????????????????????????
     *
     * @param columnNameList ???????????????ExcelModel????????????????????????????????????????????????
     * @param consumer
     * @param writeHandlers
     */
    public static <T> void buildWriteSheet(Collection<String> columnNameList, BiConsumer<CommentWriteHandler, WriteSheet> consumer,
                                           WriteHandler... writeHandlers) {
        buildWriteSheet("Sheet1", columnNameList, consumer, writeHandlers);
    }

    /**
     * ??????WriteSheet
     * <p>
     * ?????????????????????????????????????????????????????????????????????????????????
     *
     * @param sheetName      ?????????sheetName
     * @param columnNameList ???????????????ExcelModel????????????????????????????????????????????????
     * @param consumer
     * @param writeHandlers
     */
    public static <T> void buildWriteSheet(String sheetName, Collection<String> columnNameList,
                                           BiConsumer<CommentWriteHandler, WriteSheet> consumer, WriteHandler... writeHandlers) {
        ExcelWriterSheetBuilder writerSheet = EasyExcel.writerSheet().sheetName(sheetName);
        CommentWriteHandler commentWriteHandler = new CommentWriteHandler();
        writerSheet.registerWriteHandler(new LongestMatchColumnWidthStyleStrategy());
        writerSheet.registerWriteHandler(new OptionWriteHandler());
        writerSheet.registerWriteHandler(commentWriteHandler);
        for (WriteHandler handler : writeHandlers) {
            writerSheet.registerWriteHandler(handler);
        }
        if (V.notEmpty(columnNameList)) {
            writerSheet.includeColumnFiledNames(columnNameList);
        }
        consumer.accept(commentWriteHandler, writerSheet.build());
    }

    /**
     * web ??????excel
     * <p>
     * ?????????????????????????????????????????????????????????????????????????????????
     *
     * @param response
     * @param fileName
     * @param clazz         ?????????ExcelModel
     * @param dataList
     * @param writeHandlers ??????????????????
     */
    public static <T> void exportExcel(HttpServletResponse response, String fileName, Class<T> clazz, List<T> dataList,
                                       WriteHandler... writeHandlers) {
        exportExcel(response, fileName, clazz, null, dataList, writeHandlers);
    }

    /**
     * web ??????excel
     * <p>
     * ?????? ????????????????????????????????????????????????????????????????????????
     *
     * @param response
     * @param clazz          ?????????ExcelModel
     * @param dataList       ???????????????
     * @param columnNameList ???????????????ExcelModel????????????????????????????????????????????????
     * @param writeHandlers  ??????????????????
     */
    public static <T> void exportExcel(HttpServletResponse response, String fileName, Class<T> clazz, Collection<String> columnNameList,
                                       List<T> dataList, WriteHandler... writeHandlers) {
        AtomicBoolean first = new AtomicBoolean(true);
        exportExcel(response, fileName, clazz, columnNameList, () -> first.getAndSet(false) ? dataList : null, writeHandlers);
    }

    /**
     * web ??????excel
     * <p>
     * ??????????????????
     *
     * @param response
     * @param fileName
     * @param clazz
     * @param columnNameList
     * @param dataList
     * @param writeHandlers
     * @param <T>
     */
    public static <T> void exportExcel(HttpServletResponse response, String fileName, Class<T> clazz, Collection<String> columnNameList,
                                       Supplier<List<T>> dataList, WriteHandler... writeHandlers) {
        setExportExcelResponseHeader(response, fileName);
        try {
            write(response.getOutputStream(), clazz, columnNameList, Boolean.FALSE, dataList, writeHandlers);
        } catch (Exception e) {
            log.error("?????????????????????", e);
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.setHeader("code", String.valueOf(Status.FAIL_OPERATION.code()));
            try {
                response.setHeader("msg", URLEncoder.encode("??????????????????", StandardCharsets.UTF_8.name()));
            } catch (UnsupportedEncodingException ex) {
                log.error("?????????????????????", ex);
            }
        }
    }

    /**
     * ???????????????excel ?????????
     *
     * @param response
     * @param fileName
     */
    public static void setExportExcelResponseHeader(HttpServletResponse response, String fileName) {
        response.setContentType("application/x-msdownload");
        response.setCharacterEncoding("utf-8");
        response.setHeader("code", String.valueOf(Status.OK.code()));
        try {
            fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());
            response.setHeader("Content-disposition", "attachment; filename=" + fileName);
            response.setHeader("filename", fileName);
            response.setHeader("msg", URLEncoder.encode("????????????", StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            log.error("?????????????????????", e);
        }
    }

    /**
     * ????????????????????????
     *
     * @param filePath
     * @return
     */
    @Deprecated
    private static File getExcelFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            log.error("?????????????????????????????????" + filePath);
            throw new BusinessException(Status.FAIL_EXCEPTION, "?????????????????????,??????excel??????");
        }
        return file;
    }

    /**
     * ?????? Excel ??????????????????
     *
     * @param clazz ExcelModel
     * @return excel????????????
     */
    public static List<TableHead> getTableHead(Class<?> clazz) {
        TreeMap<Integer, Field> sortedAllFiledMap = new TreeMap<>();
        ClassUtils.declaredFields(clazz, sortedAllFiledMap, false, null);
        TreeMap<Integer, List<String>> headNameMap = new TreeMap<>();
        HashMap<Integer, String> fieldNameMap = new HashMap<>();
        sortedAllFiledMap.forEach((index, field) -> {
            fieldNameMap.put(index, field.getName());
            headNameMap.put(index, getHeadColumnName(field));
        });
        return buildTableHead(headNameMap, fieldNameMap);
    }

    /**
     * ?????????????????????
     *
     * @param field ?????????
     * @return ???????????????
     */
    public static List<String> getHeadColumnName(Field field) {
        ExcelProperty excelProperty = field.getAnnotation(ExcelProperty.class);
        return excelProperty == null ? Collections.singletonList(FieldUtils.resolveCglibFieldName(field))
                : Arrays.asList(excelProperty.value());
    }

    /**
     * ?????? Excel ????????????
     *
     * @param headNameMap
     * @param fieldNameMap
     * @return ????????????
     */
    public static List<TableHead> buildTableHead(Map<Integer, List<String>> headNameMap, Map<Integer, String> fieldNameMap) {
        List<TableHead> tableHead = new ArrayList<>();
        Map<String, TableHead> hashMap = new HashMap<>();
        int col = Integer.MIN_VALUE;
        for (Map.Entry<Integer, List<String>> entry : headNameMap.entrySet()) {
            if (entry.getKey() - 1 != col) {
                // ????????????
                hashMap = new HashMap<>();
            }
            col = entry.getKey();
            List<String> list = entry.getValue();
            // ????????????????????????
            boolean bool = true;
            while (list.size() > 1 && bool) {
                int lastIndex = list.size() - 1;
                if (list.get(lastIndex).equals(list.get(lastIndex - 1))) {
                    list.remove(lastIndex);
                } else {
                    bool = false;
                }
            }
            List<String> path = new ArrayList<>();
            // ????????????
            TableHead node = null;
            int lastIndex = list.size() - 1;
            for (int i = 0; i <= lastIndex; i++) {
                String name = list.get(i);
                path.add(name);
                String key = S.join(path, "???");
                TableHead item;
                if (hashMap.containsKey(key)) {
                    item = hashMap.get(key);
                } else {
                    if (i == 0) {
                        // ??????????????????
                        hashMap = new HashMap<>();
                    }
                    item = new TableHead() {{
                        setTitle(name);
                    }};
                    hashMap.put(key, item);
                    if (node == null) {
                        tableHead.add(item);
                    } else {
                        List<TableHead> children = node.getChildren();
                        if (children == null) {
                            // ??????children
                            children = new ArrayList<>();
                            node.setChildren(children);
                        }
                        if (node.getKey() != null) {
                            // ???????????????
                            TableHead originalNode = new TableHead();
                            originalNode.setKey(node.getKey());
                            originalNode.setTitle(node.getTitle());
                            node.setKey(null);
                            children.add(originalNode);
                        }
                        children.add(item);
                    }
                }
                node = item;
                if (i == lastIndex) {
                    // ??????key
                    List<TableHead> children = item.getChildren();
                    if (children == null) {
                        item.setKey(fieldNameMap.get(col));
                    } else {
                        // ??????????????????
                        TableHead thisNode = new TableHead();
                        thisNode.setKey(fieldNameMap.get(col));
                        thisNode.setTitle(item.getTitle());
                        children.add(thisNode);
                    }
                }
            }
        }
        return tableHead;
    }

    @Getter
    @Setter
    public static class TableHead {
        private String key;
        private String title;
        private List<TableHead> children;
    }
}
