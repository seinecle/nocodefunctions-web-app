///*
// * To change this license header, choose License Headers in Project Properties.
// * To change this template file, choose Tools | Templates
// * and open the template in the editor.
// */
//package net.clementlevallois.nocodeapp.web.front.io;
//
//import com.google.api.services.slides.v1.Slides;
//import com.google.api.services.slides.v1.model.AffineTransform;
//import com.google.api.services.slides.v1.model.BatchUpdatePresentationRequest;
//import com.google.api.services.slides.v1.model.CreateParagraphBulletsRequest;
//import com.google.api.services.slides.v1.model.CreateSlideRequest;
//import com.google.api.services.slides.v1.model.CreateTableRequest;
//import com.google.api.services.slides.v1.model.DeleteObjectRequest;
//import com.google.api.services.slides.v1.model.Dimension;
//import com.google.api.services.slides.v1.model.InsertTextRequest;
//import com.google.api.services.slides.v1.model.LayoutPlaceholderIdMapping;
//import com.google.api.services.slides.v1.model.LayoutReference;
//import com.google.api.services.slides.v1.model.OpaqueColor;
//import com.google.api.services.slides.v1.model.OptionalColor;
//import com.google.api.services.slides.v1.model.Page;
//import com.google.api.services.slides.v1.model.PageElement;
//import com.google.api.services.slides.v1.model.PageElementProperties;
//import com.google.api.services.slides.v1.model.ParagraphStyle;
//import com.google.api.services.slides.v1.model.Placeholder;
//import com.google.api.services.slides.v1.model.Presentation;
//import com.google.api.services.slides.v1.model.Range;
//import com.google.api.services.slides.v1.model.Request;
//import com.google.api.services.slides.v1.model.RgbColor;
//import com.google.api.services.slides.v1.model.Shape;
//import com.google.api.services.slides.v1.model.SolidFill;
//import com.google.api.services.slides.v1.model.TableCellBackgroundFill;
//import com.google.api.services.slides.v1.model.TableCellLocation;
//import com.google.api.services.slides.v1.model.TableCellProperties;
//import com.google.api.services.slides.v1.model.TableColumnProperties;
//import com.google.api.services.slides.v1.model.TableRange;
//import com.google.api.services.slides.v1.model.TableRowProperties;
//import com.google.api.services.slides.v1.model.TextStyle;
//import com.google.api.services.slides.v1.model.UpdatePageElementTransformRequest;
//import com.google.api.services.slides.v1.model.UpdateParagraphStyleRequest;
//import com.google.api.services.slides.v1.model.UpdateTableCellPropertiesRequest;
//import com.google.api.services.slides.v1.model.UpdateTableColumnPropertiesRequest;
//import com.google.api.services.slides.v1.model.UpdateTableRowPropertiesRequest;
//import com.google.api.services.slides.v1.model.UpdateTextStyleRequest;
//import java.io.IOException;
//import java.time.LocalDate;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.logging.Level;
//import java.util.logging.Logger;
//import static java.util.stream.Collectors.toList;
//import java.util.stream.IntStream;
//
///**
// *
// * @author LEVALLOIS
// */
//public class SlidesOperations {
//
//    private Slides googleSlides;
//    private final double ROW_HEIGHT = 4;
//    private final double CELL_FONT_SIZE = 10;
//    private final double HEADER_FONT_SIZE = 10;
//
//    public void init(Slides googleSlides) {
//        this.googleSlides = googleSlides;
//    }
//
//    public void customizeTitlePage(String prezId) throws IOException {
//
//        Presentation prez = googleSlides.presentations().get(prezId).execute();
//        List<Page> slides = prez.getSlides();
//        List<Request> requests;
//        BatchUpdatePresentationRequest updateRequest;
//
//        Page titlePage = slides.get(0);
//        String pageId = titlePage.getObjectId();
//        List<PageElement> pageElements = titlePage.getPageElements();
//        String titlePlaceHolderId = "";
//        String subtitlePlaceHolderId = "";
//        for (PageElement pageElement : pageElements) {
//            Shape shape = pageElement.getShape();
//            if (shape.getPlaceholder().getType().equals("CENTERED_TITLE")) {
//                titlePlaceHolderId = pageElement.getObjectId();
//            }
//            if (shape.getPlaceholder().getType().equals("SUBTITLE")) {
//                subtitlePlaceHolderId = pageElement.getObjectId();
//            }
//        }
//        LocalDate currentDate = LocalDate.now();
//
//        Request request1 = new Request()
//                .setInsertText(new InsertTextRequest()
//                        .setObjectId(titlePlaceHolderId)
//                        .setText("Report on sentiment analysis"));
//
//        Request request2 = new Request()
//                .setInsertText(new InsertTextRequest()
//                        .setObjectId(subtitlePlaceHolderId)
//                        .setText(currentDate.toString()));
//        requests = new ArrayList();
//        requests.add(request1);
//        requests.add(request2);
//        updateRequest = new BatchUpdatePresentationRequest();
//        updateRequest.setRequests(requests);
//        googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//    }
//
//    public void createSlideWithKeyMetricsUmigon(String nameSlide, String prezId, int negativeTweet, int positiveTweet, int neutralTweet, int pcNeg, int pcPos, int pcNeut, int totalTweets, String selectedLanguage) throws IOException {
//        List<Request> requests;
//        BatchUpdatePresentationRequest updateRequest;
//
//        String lang = "language: " + selectedLanguage;
//        String total = "count of texts: " + totalTweets;
//        String neg = "negative texts: " + negativeTweet + " (" + pcNeg + "%)";
//        String pos = "positive texts: " + positiveTweet + " (" + pcPos + "%)";
//        String neut = "neutral texts: " + neutralTweet + " (" + pcNeut + "%)";
//
//        Request request3 = new Request()
//                .setCreateSlide(new CreateSlideRequest()
//                        .setObjectId(nameSlide)
//                        .setSlideLayoutReference(new LayoutReference().setPredefinedLayout("TITLE_AND_BODY")));
//        updateRequest = new BatchUpdatePresentationRequest();
//        requests = new ArrayList();
//        requests.add(request3);
//        updateRequest.setRequests(requests);
//        googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//        Page pageKeyResults = googleSlides.presentations().pages().get(prezId, nameSlide).execute();
//
//        List<PageElement> pageElements = pageKeyResults.getPageElements();
//        String titleSlide2PlaceHolderId = "";
//        String bodyPlaceHolderId = "";
//        for (PageElement pageElement : pageElements) {
//            Shape shape = pageElement.getShape();
//            if (shape.getPlaceholder().getType().equals("TITLE")) {
//                titleSlide2PlaceHolderId = pageElement.getObjectId();
//            }
//            if (shape.getPlaceholder().getType().equals("BODY")) {
//                bodyPlaceHolderId = pageElement.getObjectId();
//            }
//        }
//
//        Request request4 = new Request()
//                .setInsertText(new InsertTextRequest()
//                        .setObjectId(titleSlide2PlaceHolderId)
//                        .setText("Key metrics"));
//        Request request5 = new Request()
//                .setInsertText(new InsertTextRequest()
//                        .setObjectId(bodyPlaceHolderId)
//                        .setText(lang + "\n" + total + "\n" + neg + "\n" + pos + "\n" + neut + "\n"));
//        requests = new ArrayList();
//        requests.add(request4);
//        requests.add(request5);
//        updateRequest = new BatchUpdatePresentationRequest();
//        updateRequest.setRequests(requests);
//        googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//    }
//
//    public void createSlideWithKeyMetricsOrganic(String nameSlide, String prezId, int promotedTexts, int organicTexts, int pcPromoted, int pcOrganic, int totalTweets, String selectedLanguage) throws IOException {
//        List<Request> requests;
//        BatchUpdatePresentationRequest updateRequest;
//
//        String lang = "language: " + selectedLanguage;
//        String total = "count of texts: " + totalTweets;
//        String promoted = "promoted texts: " + promotedTexts + " (" + pcPromoted + "%)";
//        String organic = "organic texts: " + organicTexts + " (" + pcOrganic + "%)";
//
//        Request request3 = new Request()
//                .setCreateSlide(new CreateSlideRequest()
//                        .setObjectId(nameSlide)
//                        .setSlideLayoutReference(new LayoutReference().setPredefinedLayout("TITLE_AND_BODY")));
//        updateRequest = new BatchUpdatePresentationRequest();
//        requests = new ArrayList();
//        requests.add(request3);
//        updateRequest.setRequests(requests);
//        googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//        Page pageKeyResults = googleSlides.presentations().pages().get(prezId, nameSlide).execute();
//
//        List<PageElement> pageElements = pageKeyResults.getPageElements();
//        String titleSlide2PlaceHolderId = "";
//        String bodyPlaceHolderId = "";
//        for (PageElement pageElement : pageElements) {
//            Shape shape = pageElement.getShape();
//            if (shape.getPlaceholder().getType().equals("TITLE")) {
//                titleSlide2PlaceHolderId = pageElement.getObjectId();
//            }
//            if (shape.getPlaceholder().getType().equals("BODY")) {
//                bodyPlaceHolderId = pageElement.getObjectId();
//            }
//        }
//
//        Request request4 = new Request()
//                .setInsertText(new InsertTextRequest()
//                        .setObjectId(titleSlide2PlaceHolderId)
//                        .setText("Key metrics"));
//        Request request5 = new Request()
//                .setInsertText(new InsertTextRequest()
//                        .setObjectId(bodyPlaceHolderId)
//                        .setText(lang + "\n" + total + "\n" + promoted + "\n" + organic + "\n"));
//        requests = new ArrayList();
//        requests.add(request4);
//        requests.add(request5);
//        updateRequest = new BatchUpdatePresentationRequest();
//        updateRequest.setRequests(requests);
//        googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//        System.out.println("key metrics page formatted");
//
//    }
//
//    public void createNewSlide(String nameSlide, String prezId, String title) throws IOException {
//
//        Presentation prez = googleSlides.presentations().get(prezId).execute();
//        List<Page> slides = prez.getSlides();
//        for (Page page : slides) {
//            if (page.getObjectId().equals(nameSlide)) {
//                System.out.println("slide with this name already exists.");
//                return;
//            }
//        }
//        Request request1 = new Request();
//        CreateSlideRequest slide = new CreateSlideRequest();
//        slide.setSlideLayoutReference(new LayoutReference().setPredefinedLayout("TITLE_AND_BODY"));
//        slide.setObjectId(nameSlide);
//        request1.setCreateSlide(slide);
//
//        BatchUpdatePresentationRequest updateRequest = new BatchUpdatePresentationRequest();
//        List<Request> requests = new ArrayList();
//        requests.add(request1);
//        updateRequest.setRequests(requests);
//
//        googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//        Page pageKeyResults = googleSlides.presentations().pages().get(prezId, nameSlide).execute();
//
//        List<PageElement> pageElements = pageKeyResults.getPageElements();
//        String titleSlide2PlaceHolderId = "";
//        String bodyPlaceHolderId = "";
//        for (PageElement pageElement : pageElements) {
//            Shape shape = pageElement.getShape();
//            if (shape.getPlaceholder().getType().equals("TITLE")) {
//                titleSlide2PlaceHolderId = pageElement.getObjectId();
//            }
//            if (shape.getPlaceholder().getType().equals("BODY")) {
//                bodyPlaceHolderId = pageElement.getObjectId();
//            }
//        }
//
//        Request request2 = new Request()
//                .setInsertText(new InsertTextRequest()
//                        .setObjectId(titleSlide2PlaceHolderId)
//                        .setText(title));
//
//        slide.setObjectId(nameSlide);
//        request1.setCreateSlide(slide);
//
//        updateRequest = new BatchUpdatePresentationRequest();
//        requests = new ArrayList();
//        requests.add(request2);
//        updateRequest.setRequests(requests);
//
//        googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//        System.out.println("slide created with title:" + title);
//    }
//
//    public void createNewSlideNoTextElement(String nameSlide, String prezId) throws IOException {
//
//        Presentation prez = googleSlides.presentations().get(prezId).execute();
//        List<Page> slides = prez.getSlides();
//        for (Page page : slides) {
//            if (page.getObjectId().equals(nameSlide)) {
//                System.out.println("slide with this name already exists.");
//                return;
//            }
//        }
//        Request request = new Request();
//        CreateSlideRequest slide = new CreateSlideRequest();
//        slide.setSlideLayoutReference(new LayoutReference().setPredefinedLayout("BLANK"));
//
//        slide.setObjectId(nameSlide);
//        request.setCreateSlide(slide);
//
//        BatchUpdatePresentationRequest updateRequest = new BatchUpdatePresentationRequest();
//        List<Request> requests = new ArrayList();
//        requests.add(request);
//        updateRequest.setRequests(requests);
//
//        googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//        System.out.println("slide created");
//    }
//
//    public void createNewSlideWithTitleAndBodyPlaceHolders(String nameSlide, String prezId) throws IOException {
//
//        Presentation prez = googleSlides.presentations().get(prezId).execute();
//        List<Page> slides = prez.getSlides();
//        for (Page page : slides) {
//            if (page.getObjectId().equals(nameSlide)) {
//                System.out.println("slide with this name already exists.");
//                return;
//            }
//        }
//
//        LayoutPlaceholderIdMapping titlePlaceHolder = new LayoutPlaceholderIdMapping();
//        titlePlaceHolder.setLayoutPlaceholder(new Placeholder().setType("TITLE")
//                .setParentObjectId(nameSlide + "slideTitle")
//                .setIndex(0)
//        );
//
//        LayoutPlaceholderIdMapping bodyPlaceHolder = new LayoutPlaceholderIdMapping();
//        titlePlaceHolder.setLayoutPlaceholder(new Placeholder().setType("BODY")
//                .setParentObjectId(nameSlide + "slideBody")
//                .setIndex(0)
//        );
//
//        List<LayoutPlaceholderIdMapping> mappings = new ArrayList();
//        mappings.add(titlePlaceHolder);
//        mappings.add(bodyPlaceHolder);
//
//        Request request1 = new Request()
//                .setCreateSlide(new CreateSlideRequest()
//                        .setSlideLayoutReference(new LayoutReference().setPredefinedLayout("TITLE_AND_BODY"))
//                        .setPlaceholderIdMappings(mappings));
//
//        BatchUpdatePresentationRequest updateRequest = new BatchUpdatePresentationRequest();
//        List<Request> requests = new ArrayList();
//        requests.add(request1);
//        updateRequest.setRequests(requests);
//
//        googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//        System.out.println("slide created");
//    }
//
//    public void insertTextCommentSlide(String nameSlide, String prezId) throws IOException {
//
//        Page page = googleSlides.presentations().pages().get(prezId, nameSlide).execute();
//
//        List<PageElement> pageElements = page.getPageElements();
//        String titleSlide2PlaceHolderId = "";
//        String bodyPlaceHolderId = "";
//        for (PageElement pageElement : pageElements) {
//            Shape shape = pageElement.getShape();
//            if (shape.getPlaceholder().getType().equals("TITLE")) {
//                titleSlide2PlaceHolderId = pageElement.getObjectId();
//            }
//            if (shape.getPlaceholder().getType().equals("BODY")) {
//                bodyPlaceHolderId = pageElement.getObjectId();
//            }
//        }
//
//        Request request5 = new Request()
//                .setInsertText(new InsertTextRequest()
//                        .setObjectId(bodyPlaceHolderId)
//                        .setText("Umigon is the best performing tool for sentiment analysis of social media in English and French.\n"
//                                + "Hashtags, emojis, and punctuation signs are taken into consideration, do not remove them from your data when running the analysis.\n"
//                                + "If you publish the results of this analysis, you must cite the tool:\n"
//                                + "Levallois,  Clement. “Umigon: Sentiment  analysis  on  Tweets  based  on  terms  lists  and  heuristics”. Proceedings of the 7th International Workshop on Semantic Evaluation(SemEval), 2013, Atlanta, Georgia\n"
//                                + "Mistakes and inaccuracies can be fixed when you report them - use the buttons in the app or drop an email at analysis@exploreyourdata.com"));
//        Request request8 = new Request()
//                .setCreateParagraphBullets(new CreateParagraphBulletsRequest()
//                        .setObjectId(bodyPlaceHolderId)
//                        .setTextRange(new Range().setStartIndex(0).setEndIndex(295).setType("FIXED_RANGE"))
//                        .setBulletPreset("BULLET_LEFTTRIANGLE_DIAMOND_DISC"));
//        Request request9 = new Request()
//                .setCreateParagraphBullets(new CreateParagraphBulletsRequest()
//                        .setObjectId(bodyPlaceHolderId)
//                        .setTextRange(new Range().setStartIndex(507).setEndIndex(643).setType("FIXED_RANGE"))
//                        .setBulletPreset("BULLET_LEFTTRIANGLE_DIAMOND_DISC"));
//        Request request6 = new Request()
//                .setUpdateTextStyle(new UpdateTextStyleRequest()
//                        .setObjectId(bodyPlaceHolderId)
//                        .setTextRange(new Range().setStartIndex(401).setEndIndex(482).setType("FIXED_RANGE"))
//                        .setStyle(new TextStyle().setItalic(true))
//                        .setFields("italic"));
//        Request request7 = new Request()
//                .setUpdateTextStyle(new UpdateTextStyleRequest()
//                        .setObjectId(bodyPlaceHolderId)
//                        .setTextRange(new Range().setStartIndex(296).setEndIndex(505).setType("FIXED_RANGE"))
//                        .setStyle(new TextStyle().setFontSize(new Dimension().setMagnitude(12d).setUnit("PT")))
//                        .setFields("fontSize"));
//
//        Request request10 = new Request()
//                .setUpdateParagraphStyle(new UpdateParagraphStyleRequest()
//                        .setObjectId(bodyPlaceHolderId)
//                        .setTextRange(new Range().setStartIndex(296).setEndIndex(505).setType("FIXED_RANGE"))
//                        .setStyle(new ParagraphStyle()
//                                .setIndentStart(new Dimension().setMagnitude(30d).setUnit("PT")))
//                        .setFields("indentStart"));
//
//        List<Request> requests = new ArrayList();
//        requests.add(request5);
//        requests.add(request6);
//        requests.add(request7);
//        requests.add(request8);
//        requests.add(request9);
//        requests.add(request10);
//        BatchUpdatePresentationRequest updateRequest = new BatchUpdatePresentationRequest();
//        updateRequest.setRequests(requests);
//        googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//    }
//
//    public void insertTable(String tableId, String slideId, String prezId, int row, int column) {
//        try {
//            List<Request> requests = new ArrayList();
//            Request createTable = new Request()
//                    .setCreateTable(new CreateTableRequest()
//                            .setObjectId(tableId)
//                            .setRows(row)
//                            .setColumns(column)
//                            .setElementProperties(new PageElementProperties()
//                                    .setPageObjectId(slideId)
//                            ));
//            requests.add(createTable);
//
//            BatchUpdatePresentationRequest updateRequest = new BatchUpdatePresentationRequest();
//            updateRequest.setRequests(requests);
//            googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//            Request formatColumnsText = new Request()
//                    .setUpdateTableColumnProperties(new UpdateTableColumnPropertiesRequest()
//                            .setObjectId(tableId)
//                            .setColumnIndices(List.of(0, 2))
//                            .setTableColumnProperties(new TableColumnProperties()
//                                    .setColumnWidth(new Dimension().setMagnitude(120d).setUnit("PT")
//                                    ))
//                            .setFields("columnWidth"));
//            Request formatColumnsFreq = new Request()
//                    .setUpdateTableColumnProperties(new UpdateTableColumnPropertiesRequest()
//                            .setObjectId(tableId)
//                            .setColumnIndices(List.of(1, 3))
//                            .setTableColumnProperties(new TableColumnProperties()
//                                    .setColumnWidth(new Dimension().setMagnitude(40d).setUnit("PT")
//                                    ))
//                            .setFields("columnWidth"));
//
//            Request formatRows = new Request()
//                    .setUpdateTableRowProperties(new UpdateTableRowPropertiesRequest()
//                            .setObjectId(tableId)
//                            .setRowIndices(IntStream.rangeClosed(0, row - 1).boxed().collect(toList()))
//                            .setTableRowProperties(new TableRowProperties()
//                                    .setMinRowHeight(new Dimension().setMagnitude(ROW_HEIGHT).setUnit("PT")
//                                    ))
//                            .setFields("minRowHeight"));
//
//            Request moveTableToTheRight = new Request()
//                    .setUpdatePageElementTransform(new UpdatePageElementTransformRequest()
//                            .setObjectId(tableId)
//                            .setApplyMode("RELATIVE")
//                            .setTransform(new AffineTransform()
//                                    .setScaleX(1d)
//                                    .setScaleY(1d)
//                                    .setTranslateX(250d)
//                                    .setTranslateY(0d)
//                                    .setUnit("PT")));
//
//            requests = new ArrayList();
//            requests.add(formatColumnsText);
//            requests.add(formatColumnsFreq);
//            requests.add(formatRows);
//            requests.add(moveTableToTheRight);
//
//            updateRequest = new BatchUpdatePresentationRequest();
//            updateRequest.setRequests(requests);
//            googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//        } catch (IOException ex) {
//            Logger.getLogger(SlidesOperations.class.getName()).log(Level.SEVERE, null, ex);
//        }
//    }
//
//    public void addHeaders(String tableId, String prezId, List<String> headers) {
//        try {
//            List<Request> requests = new ArrayList();
//            int colIndex = 0;
//            for (String entry : headers) {
//                Request insertTerm = new Request()
//                        .setInsertText(new InsertTextRequest()
//                                .setObjectId(tableId)
//                                .setText(entry)
//                                .setInsertionIndex(0)
//                                .setCellLocation(new TableCellLocation()
//                                        .setRowIndex(0)
//                                        .setColumnIndex(colIndex++)
//                                ));
//                requests.add(insertTerm);
//            }
//
//            BatchUpdatePresentationRequest updateRequest = new BatchUpdatePresentationRequest();
//            updateRequest.setRequests(requests);
//            googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//            colIndex = 0;
//            requests = new ArrayList();
//            Request formatHeaderCells = new Request()
//                    .setUpdateTableCellProperties(new UpdateTableCellPropertiesRequest()
//                            .setObjectId(tableId)
//                            .setTableRange(new TableRange().setLocation(new TableCellLocation().setRowIndex(0).setColumnIndex(colIndex))
//                                    .setRowSpan(1)
//                                    .setColumnSpan(4))
//                            .setTableCellProperties(new TableCellProperties()
//                                    .setTableCellBackgroundFill(new TableCellBackgroundFill()
//                                            .setSolidFill(new SolidFill()
//                                                    .setColor(new OpaqueColor().setRgbColor(new RgbColor()
//                                                            .setBlue(0F).setGreen(0F).setRed(0F))))))
//                            .setFields("tableCellBackgroundFill.solidFill.color"));
//
//            requests.add(formatHeaderCells);
//            updateRequest = new BatchUpdatePresentationRequest();
//            updateRequest.setRequests(requests);
//            googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//            requests = new ArrayList();
//            colIndex = 0;
//            for (String entry : headers) {
//                double fontSize;
//                if (entry.equals("count")) {
//                    fontSize = HEADER_FONT_SIZE - 2;
//                } else {
//                    fontSize = HEADER_FONT_SIZE;
//                }
//
//                Request formatTextHeader = new Request()
//                        .setUpdateTextStyle(new UpdateTextStyleRequest()
//                                .setObjectId(tableId)
//                                .setTextRange(new Range().setType("ALL"))
//                                .setCellLocation(new TableCellLocation().setRowIndex(0).setColumnIndex(colIndex))
//                                .setStyle(new TextStyle()
//                                        .setFontSize(new Dimension().setMagnitude(fontSize).setUnit("PT"))
//                                        .setBold(Boolean.TRUE)
//                                        .setFontFamily("Cambria")
//                                        .setForegroundColor(
//                                                new OptionalColor().setOpaqueColor(
//                                                        new OpaqueColor().setRgbColor(new RgbColor()
//                                                                .setBlue(1.0f)
//                                                                .setGreen(1.0f)
//                                                                .setRed(1.0f)))))
//                                .setFields("foregroundColor,bold,fontFamily,fontSize"));
//                colIndex++;
//                requests.add(formatTextHeader);
//            }
//            if (requests.isEmpty()) {
//                return;
//            }
//
//            updateRequest = new BatchUpdatePresentationRequest();
//            updateRequest.setRequests(requests);
//            googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//        } catch (IOException ex) {
//            Logger.getLogger(SlidesOperations.class.getName()).log(Level.SEVERE, null, ex);
//        }
//    }
//
//    public void insertContentInTable(String prezId, String tableId, int colIndex, List<Map.Entry<String, Integer>> data) {
//        try {
//            List<Request> requests = new ArrayList();
//            int rowIndex = 1;
//            for (Map.Entry<String, Integer> entry : data) {
//                String term = entry.getKey();
//                if (term.isBlank()) {
//                    term = "__";
//                }
//                Integer freq = entry.getValue();
//                Request insertTerm = new Request()
//                        .setInsertText(new InsertTextRequest()
//                                .setObjectId(tableId)
//                                .setText(term)
//                                .setInsertionIndex(0)
//                                .setCellLocation(new TableCellLocation()
//                                        .setRowIndex(rowIndex)
//                                        .setColumnIndex(colIndex)
//                                ));
//                Request insertFreq = new Request()
//                        .setInsertText(new InsertTextRequest()
//                                .setObjectId(tableId)
//                                .setText(String.valueOf(freq))
//                                .setInsertionIndex(0)
//                                .setCellLocation(new TableCellLocation()
//                                        .setRowIndex(rowIndex)
//                                        .setColumnIndex(colIndex + 1)
//                                ));
//                rowIndex++;
//                requests.add(insertTerm);
//                requests.add(insertFreq);
//            }
//            if (requests.isEmpty()) {
//                return;
//            }
//
//            BatchUpdatePresentationRequest updateRequest = new BatchUpdatePresentationRequest();
//            updateRequest.setRequests(requests);
//            googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//            rowIndex = 1;
//            requests = new ArrayList();
//            for (Map.Entry<String, Integer> entry : data) {
//                Request formatText1 = new Request()
//                        .setUpdateTextStyle(new UpdateTextStyleRequest()
//                                .setObjectId(tableId)
//                                .setTextRange(new Range().setType("ALL"))
//                                .setCellLocation(new TableCellLocation().setRowIndex(rowIndex).setColumnIndex(colIndex))
//                                .setStyle(new TextStyle().setFontSize(new Dimension().setMagnitude(CELL_FONT_SIZE).setUnit("PT")))
//                                .setFields("fontSize"));
//
//                Request formatText2 = new Request()
//                        .setUpdateTextStyle(new UpdateTextStyleRequest()
//                                .setObjectId(tableId)
//                                .setTextRange(new Range().setType("ALL"))
//                                .setCellLocation(new TableCellLocation().setRowIndex(rowIndex).setColumnIndex(colIndex + 1))
//                                .setStyle(new TextStyle().setFontSize(new Dimension().setMagnitude(CELL_FONT_SIZE).setUnit("PT")))
//                                .setFields("fontSize"));
//
//                rowIndex++;
//                requests.add(formatText1);
//                requests.add(formatText2);
//            }
//            if (requests.isEmpty()) {
//                return;
//            }
//
//            updateRequest = new BatchUpdatePresentationRequest();
//            updateRequest.setRequests(requests);
//            googleSlides.presentations().batchUpdate(prezId, updateRequest).execute();
//
//        } catch (IOException ex) {
//            System.out.println("error in setting font size");
//            Logger.getLogger(SlidesOperations.class.getName()).log(Level.SEVERE, null, ex);
//        }
//    }
//}
