/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.tests;


//import com.google.api.services.sheets.v4.Sheets;

public class GoogleSheetsLiveTest {

//    private static Sheets sheetsService;

    // this id can be replaced with your spreadsheet id
    // otherwise be advised that multiple people may run this test and update the public spreadsheet
    private static final String SPREADSHEET_ID = "1f6y-qicv8ghsQwxl5n1In7J693umLIuAGDt6S539AIc";

//    @BeforeClass
//    public static void setup() throws GeneralSecurityException, IOException {
//        GoogleAuthorizeUtil worker = new GoogleAuthorizeUtil();
//        worker.initSheetsService();
//        sheetsService = worker.getSheetsService();
//    }

//    @Test
//    public void whenWriteSheet_thenReadSheetOk() throws IOException {
//        ValueRange body = new ValueRange().setValues(Arrays.asList(Arrays.asList("Expenses January"), Arrays.asList("books", "30"), Arrays.asList("pens", "10"), Arrays.asList("Expenses February"), Arrays.asList("clothes", "20"), Arrays.asList("shoes", "5")));
//        UpdateValuesResponse result = sheetsService.spreadsheets().values().update(SPREADSHEET_ID, "A1", body).setValueInputOption("RAW").execute();
//
//        List<ValueRange> data = new ArrayList<>();
//        data.add(new ValueRange().setRange("D1").setValues(Arrays.asList(Arrays.asList("January Total", "=B2+B3"))));
//        data.add(new ValueRange().setRange("D4").setValues(Arrays.asList(Arrays.asList("February Total", "=B5+B6"))));
//
//        BatchUpdateValuesRequest batchBody = new BatchUpdateValuesRequest().setValueInputOption("USER_ENTERED").setData(data);
//        BatchUpdateValuesResponse batchResult = sheetsService.spreadsheets().values().batchUpdate(SPREADSHEET_ID, batchBody).execute();
//
//        List<String> ranges = Arrays.asList("E1", "E4");
//        BatchGetValuesResponse readResult = sheetsService.spreadsheets().values().batchGet(SPREADSHEET_ID).setRanges(ranges).execute();
//
//        ValueRange januaryTotal = readResult.getValueRanges().get(0);
//        assertEquals((String)januaryTotal.getValues().get(0).get(0),"40");
//
//        ValueRange febTotal = readResult.getValueRanges().get(1);
//        assertEquals(febTotal.getValues().get(0).get(0),"25");
//
//        ValueRange appendBody = new ValueRange().setValues(Arrays.asList(Arrays.asList("Total", "=E1+E4")));
//        AppendValuesResponse appendResult = sheetsService.spreadsheets().values().append(SPREADSHEET_ID, "A1", appendBody).setValueInputOption("USER_ENTERED").setInsertDataOption("INSERT_ROWS").setIncludeValuesInResponse(true).execute();
//
//        ValueRange total = appendResult.getUpdates().getUpdatedData();
//        assertEquals(total.getValues().get(0).get(1),"65");
//    }


//    @Test
//    public void whenCreateSpreadSheet_thenIdOk() throws IOException {
//        Spreadsheet spreadSheet = new Spreadsheet().setProperties(new SpreadsheetProperties().setTitle("My Spreadsheet"));
//        Spreadsheet result = sheetsService.spreadsheets().create(spreadSheet).execute();
//
//        Assert.assertNotNull(result.getSpreadsheetId());
//    }

}
