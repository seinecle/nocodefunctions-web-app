///*
// * To change this license header, choose License Headers in Project Properties.
// * To change this template file, choose Tools | Templates
// * and open the template in the editor.
// */
//package net.clementlevallois.nocodeapp.web.front.importdata;
//
//import com.google.api.client.auth.oauth2.Credential;
//import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
//import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
//import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
//import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
//import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
//import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
//import com.google.api.client.http.javanet.NetHttpTransport;
//import com.google.api.client.json.JsonFactory;
//import com.google.api.client.json.gson.GsonFactory;
//import com.google.api.client.util.store.FileDataStoreFactory;
//import com.google.api.services.sheets.v4.Sheets;
//import com.google.api.services.sheets.v4.SheetsScopes;
//import com.google.api.services.sheets.v4.model.Sheet;
//import com.google.api.services.sheets.v4.model.Spreadsheet;
//import com.google.api.services.sheets.v4.model.ValueRange;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.io.Serializable;
//import java.security.GeneralSecurityException;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.UUID;
//import java.util.logging.Level;
//import java.util.logging.Logger;
//import jakarta.enterprise.context.SessionScoped;
//import jakarta.faces.context.ExternalContext;
//import jakarta.faces.context.FacesContext;
//import jakarta.inject.Inject;
//import jakarta.inject.Named;
//import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
//import net.clementlevallois.nocodeapp.web.front.functions.GazeBean;
//import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
//import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
//
///**
// *
// * @author LEVALLOIS
// */
//@Named(value = "googleSheetsImportBean")
//@SessionScoped
//public class GoogleSheetsImportBean implements Serializable {
//
//    private GoogleAuthorizationCodeFlow authFlow;
//    private String code;
//    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);
//    private static final String CREDENTIALS_FILE_PATH = "google-sheets-client-secret.json";
//    private static final String APPLICATION_NAME = "nocode functions G Sheet import";
//    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
//    private static final String TOKENS_DIRECTORY_PATH = "tokens";
//    private Sheets service;
//
//    @Inject
//    NotificationService notifService;
//
//    @Inject
//    GazeBean gazeBean;
//
//    @Inject
//    DataImportBean inputData;
//
//    @Inject
//    SessionBean sessionBean;
//
//    public GoogleSheetsImportBean() {
//    }
//
//    public String getCode() {
//        return code;
//    }
//
//    public void setCode(String code) {
//        this.code = code;
//    }
//
//    public void onloadingRedirectPage() {
//        try {
//            String baseURL;
//            baseURL = RemoteLocal.getDomain();
//            Credential credentials;
//            if (authFlow == null) {
//                System.out.println("authFlow was null");
//            }
//            try {
//                GoogleAuthorizationCodeTokenRequest tokenRequest = authFlow.newTokenRequest(code).setRedirectUri(baseURL + "google_auth.html");
//                GoogleTokenResponse tokenResponse = tokenRequest.execute();
//                credentials = authFlow.createAndStoreCredential(tokenResponse, UUID.randomUUID().toString());
//                final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
//                service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
//                        .setApplicationName(APPLICATION_NAME)
//                        .build();
//
//            } catch (IOException | GeneralSecurityException ex) {
//                Logger.getLogger(GoogleSheetsImportBean.class.getName()).log(Level.SEVERE, null, ex);
//            }
//            notifService.create("access granted to the Google Sheet ⭐");
//            FacesContext context = FacesContext.getCurrentInstance();
//
//            String redirectPage = "";
//            if (sessionBean.getFunction().equals("gaze")) {
//                if (gazeBean.getOption().equals("1")){
//                    redirectPage = "import_your_data_network_builder_option_1.html";
//                }
//                if (gazeBean.getOption().equals("2")){
//                    redirectPage = "import_your_data_network_builder_option_2.html";
//                }
//            }else{
//                redirectPage = "import_your_data.html";
//            }
//            context.getExternalContext().redirect(baseURL + redirectPage);
//        } catch (IOException ex) {
//            Logger.getLogger(GoogleSheetsImportBean.class.getName()).log(Level.SEVERE, null, ex);
//        }
//    }
//
//    public String goToConnectURL() throws IOException {
//        try {
//            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
//
//            // Load client secrets.
//            InputStream in = GoogleSheetsImportBean.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
//            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
//
//            // Build flow and trigger user authorization request.
//            authFlow = new GoogleAuthorizationCodeFlow.Builder(
//                    HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
//                    .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
//                    .setAccessType("offline")
//                    .build();
//            GoogleAuthorizationCodeRequestUrl newAuthorizationUrl = authFlow.newAuthorizationUrl();
//            String baseURL = RemoteLocal.getDomain();
//            GoogleAuthorizationCodeRequestUrl redirectUri = newAuthorizationUrl.setRedirectUri(baseURL + "google_auth.html");
//            ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
//            externalContext.redirect(redirectUri.toString());
//
//        } catch (GeneralSecurityException ex) {
//            Logger.getLogger(GoogleSheetsImportBean.class.getName()).log(Level.SEVERE, null, ex);
//        }
//        return "error.xhtml";
//    }
//
//    public GoogleAuthorizationCodeFlow getAuthFlow() {
//        return authFlow;
//    }
//
//    public Sheets getService() {
//        return service;
//    }
//
//    public void setService(Sheets service) {
//        this.service = service;
//    }
//
//    public List<String> getSheetsNames(String spreadsheetId) {
//        List<String> sheetsNames = new ArrayList();
//        if (service == null) {
//            return sheetsNames;
//        }
//        try {
//            Spreadsheet response1 = service.spreadsheets().get(spreadsheetId).setIncludeGridData(false).execute();
//
//            List<Sheet> workSheetList = response1.getSheets();
//
//            for (Sheet sheet : workSheetList) {
//                sheetsNames.add(sheet.getProperties().getTitle());
//            }
//        } catch (IOException ex) {
//            Logger.getLogger(GoogleSheetsImportBean.class.getName()).log(Level.SEVERE, null, ex);
//            return null;
//        }
//        return sheetsNames;
//    }
////
////    public Map<Integer, String> getLinesWithInput(String spreadsheetId, String sheetName, String column, Boolean hasHeaders) {
////        Map<Integer, String> lines = new TreeMap();
////        if (service == null) {
////            return lines;
////        }
////        if (column == null) {
////            column = "A";
////        }
////        if (sheetName == null) {
////            List<String> sheetsNames = getSheetsNames(spreadsheetId);
////            if (sheetsNames.isEmpty()) {
////                System.out.println("found no sheet in the spreadsheet");
////                return lines;
////            } else {
////                sheetName = sheetsNames.get(0);
////            }
////        }
////        if (hasHeaders == null) {
////            hasHeaders = false;
////        }
////        try {
////            String range = sheetName + "!" + column.toUpperCase() + ":" + column.toUpperCase();
////            ValueRange response = service.spreadsheets().values().get(spreadsheetId, range).execute();
////            List<List<Object>> values = response.getValues();
////            if (values == null || values.isEmpty()) {
////                System.out.println("No data found.");
////            } else {
////                if (hasHeaders) {
////                    values.remove(0);
////                }
////                int rowNumber = 1;
////                for (List row : values) {
////                    String value = (String) row.get(0);
////                    if (value == null) {
////                        break;
////                    }
////                    lines.put(rowNumber++, value);
////                }
////            }
////
////        } catch (IOException ex) {
////            Logger.getLogger(GoogleBean.class.getName()).log(Level.SEVERE, null, ex);
////        }
////        return lines;
////    }
//
//    public List<SheetModel> readGSheetData(String spreadsheetId) {
//        List<SheetModel> sheets = new ArrayList();
//        List<String> sheetsNames = getSheetsNames(spreadsheetId);
//        if (sheetsNames == null) {
//            notifService.create("😰 the url could not be read. Is your file a Google Sheet? (Excel files opened as Google Sheets should be opened as a file, not as a Google Sheet).");
//            return sheets;
//        }
//        int firstSheet = 0;
//        for (String sheetName : sheetsNames) {
//            SheetModel sheetModel = new SheetModel();
//            List<ColumnModel> headerNames = new ArrayList();
//            try {
//                String range = sheetName;
//                String valueRenderOption = "UNFORMATTED_VALUE";
//                String dateTimeRenderOption = "FORMATTED_STRING";
//                Sheets.Spreadsheets.Values.Get request = service.spreadsheets().values().get(spreadsheetId, range);
//                request.setValueRenderOption(valueRenderOption);
//                request.setDateTimeRenderOption(dateTimeRenderOption);
//
//                ValueRange response = request.execute();
//                List<List<Object>> values = response.getValues();
//                if (values == null || values.isEmpty()) {
//                    System.out.println("No data found in the gSheet.");
//                    return sheets;
//                } else {
//                    int rowNumber = 0;
//                    for (List row : values) {
//                        int colNumber = 0;
//                        for (Object cellValue : row) {
//                            String cellValueString = (String) cellValue;
//                            if (rowNumber == 0) {
//                                ColumnModel cm;
//                                cm = new ColumnModel(String.valueOf(colNumber), cellValueString);
//                                headerNames.add(cm);
//                            }
//                            CellRecord cellRecord = new CellRecord(rowNumber, colNumber, cellValueString);
//                            sheetModel.addCellRecord(cellRecord);
//                            colNumber++;
//                        }
//                        rowNumber++;
//                    }
//                }
//
//            } catch (IOException ex) {
//                Logger.getLogger(GoogleSheetsImportBean.class.getName()).log(Level.SEVERE, null, ex);
//            }
//            sheetModel.setName(sheetName);
//            sheetModel.setTableHeaderNames(headerNames);
//            sheets.add(sheetModel);
//            firstSheet++;
//
//            // if we are computing cooccurrences, we need to set the sheet name and the column to zero.
//            if (sessionBean.getFunction().equals("gaze") && gazeBean != null && gazeBean.getOption().equals("1")) {
//                inputData.setSelectedSheetName(sheetName);
//            }
//
//        }
//        return sheets;
//    }
//
//    
//}
