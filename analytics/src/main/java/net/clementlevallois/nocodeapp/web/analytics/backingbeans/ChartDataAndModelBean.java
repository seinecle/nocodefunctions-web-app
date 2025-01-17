/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.analytics.backingbeans;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import net.clementlevallois.utils.Multiset;
import org.primefaces.model.charts.ChartData;
import org.primefaces.model.charts.axes.cartesian.CartesianScales;
import org.primefaces.model.charts.axes.cartesian.linear.CartesianLinearAxes;
import org.primefaces.model.charts.bar.BarChartDataSet;
import org.primefaces.model.charts.bar.BarChartModel;
import org.primefaces.model.charts.bar.BarChartOptions;
import org.primefaces.model.charts.hbar.HorizontalBarChartModel;
import org.primefaces.model.charts.optionconfig.legend.Legend;
import org.primefaces.model.charts.optionconfig.title.Title;

/**
 *
 * @author LEVALLOIS
 */
@ApplicationScoped
public class ChartDataAndModelBean {

    private BarChartModel modelLaunch;
    private String filePathString = "";
    private final String filePathStringWindows = "C:\\Users\\clevallois.GOBELINS\\open\\nocode-app-web-front\\analytics";
    private final String filePathStringLinux = "/home/waouh/webapp";

    private final String PAST_YEARS = "past_years.txt";
    private final String PAST_MONTHS = "past_months.txt";
    private final String CURRENT_LOG = "current_year_log.txt";
    private final String ALL_LOGS = "all_logs.txt";

    private int currentMonthLastTimeWeChecked = 01;
    private int currentYearLastTimeWeChecked = 2025;
    private final Set<String> userAgentsToIgnore = Set.of("test", "umigon fr", "umigon en", "umigon es", "labellling", "organic es", "organic en", "organic fr", "null", "bot", "hacker");
    private final List<String> rgbColors = List.of("rgb(158, 1, 66)", "rgb(213, 62, 79)", "rgb(244, 109, 67)", "rgb(259, 174, 97)", "rgb(254, 224, 139)", "rgb(230, 245, 152)", "rgb(171, 221, 164)", "rgb(102, 194, 165)", "rgb(50, 136, 189)", "rgb(94, 79, 162)", "rgb(158, 1, 66)", "rgb(213, 62, 79)", "rgb(244, 109, 67)", "rgb(259, 174, 97)", "rgb(254, 224, 139)");

    /*
    
    Logs are stored in this way:
    
    past years:
    2021,cowo,234
    
    past months up to current month:
    2024_2,cowo,12
    
    current month:
    1709256116,umigon,Desktop,Unknow,Chrome
    
    
    Each time we change month,
    logs of current month are collapsed into past months
    logs of current month are erased
    
    Each time we change year,
    logs of current month are collapsed into past months
    logs of current month are erased
    logs of past months are collapsed into past years
    logs of past months are erased
    
     */
    public ChartDataAndModelBean() {

    }

    public static void main(String[] args) throws IOException {
        new ChartDataAndModelBean().handleAndReadEvents();
//        new ChartDataAndModelBean().cleanAndResetToCurrentDate();
    }

    public void handleAndReadEvents() throws IOException {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            filePathString = filePathStringWindows;
        } else {
            filePathString = filePathStringLinux;
        }

        int monthCurrent = LocalDateTime.now().getMonthValue();
        int yearCurrent = LocalDateTime.now().getYear();
        boolean yearChanged = currentYearLastTimeWeChecked != yearCurrent;
        boolean monthChanged = currentMonthLastTimeWeChecked != monthCurrent;

        if (monthChanged) {
            updateOperationsBecauseMonthChanged();
        }
        if (yearChanged) {
            updateOperationsBecauseYearChanged();
        }
    }

    private void cleanAndResetToCurrentDate() throws IOException {

        int yearCurrent = LocalDate.now().getYear();

        Path pathToCurrentYearLogFile = Path.of(filePathString, CURRENT_LOG);
        Path pathToAllYearsLogFile = Path.of(filePathString, ALL_LOGS);
        String logsFromCurrentYear = Files.readString(pathToCurrentYearLogFile);
        Files.writeString(pathToAllYearsLogFile, logsFromCurrentYear, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        CsvParserSettings settings = new CsvParserSettings();
        settings.getFormat().setLineSeparator("\n");
        settings.setMaxCharsPerColumn(-1);
        CsvParser parser = new CsvParser(settings);

        // Data structure to store all different log events and their counts per year
        Map<Integer, Multiset> spreadPerAllYears = new TreeMap();
        Map<String, Multiset> spreadPerCurrentYear = new TreeMap();

        List<String[]> rows = parser.parseAll(pathToAllYearsLogFile.toFile());
        for (String[] row : rows) {
            String unixTimeStamp = row[0];
            LocalDateTime ofEpochSecond = LocalDateTime.ofEpochSecond(Integer.parseInt(unixTimeStamp), 0, ZoneOffset.UTC);
            int year = ofEpochSecond.getYear();
            int month = ofEpochSecond.getMonthValue();
            String function = row[1];
            if (function == null || function.equals("null")){
                continue;
            }

            if (year != yearCurrent) {
                // adding to data structure for past_years
                Multiset<String> logEventsForAGivenYear = spreadPerAllYears.getOrDefault(year, new Multiset());
                logEventsForAGivenYear.addOne(function);
                spreadPerAllYears.put(year, logEventsForAGivenYear);
            } else {
                // adding to data structure for past_months
                String key = String.valueOf(year) + "_" + String.valueOf(month);
                Multiset<String> logEventsForAGivenYear = spreadPerCurrentYear.getOrDefault(key, new Multiset());
                logEventsForAGivenYear.addOne(function);
                spreadPerCurrentYear.put(key, logEventsForAGivenYear);
            }
        }
        
        // now we can write proper data to past_months.txt, mast_years.txt
        
        // first writing to past_years.txt
        
        Iterator<Map.Entry<Integer, Multiset>> iteratorOnYears = spreadPerAllYears.entrySet().iterator();
        StringBuilder logsToCollapse = new StringBuilder();
        while (iteratorOnYears.hasNext()) {
            Map.Entry<Integer, Multiset> nextEntry = iteratorOnYears.next();
            Multiset<String> values = nextEntry.getValue();
            StringBuilder sb = new StringBuilder();
            for (String value : values.getElementSet()) {
                sb.append(nextEntry.getKey());
                sb.append(",");
                sb.append(value);
                sb.append(",");
                sb.append(values.getCount(value));
                sb.append(System.lineSeparator());
            }
            logsToCollapse.append(sb.toString());
        }
        Path pathToPastYearsValuesFile = Path.of(filePathString, PAST_YEARS);
        Files.writeString(pathToPastYearsValuesFile, logsToCollapse.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE);
        
        // then writing to PAST_MONTHS
        
        Iterator<Map.Entry<String, Multiset>> iteratorOnMonths = spreadPerCurrentYear.entrySet().iterator();
        logsToCollapse = new StringBuilder();
        while (iteratorOnMonths.hasNext()) {
            Map.Entry<String, Multiset> nextEntry = iteratorOnMonths.next();
            Multiset<String> values = nextEntry.getValue();
            StringBuilder sb = new StringBuilder();
            for (String value : values.getElementSet()) {
                sb.append(nextEntry.getKey());
                sb.append(",");
                sb.append(value);
                sb.append(",");
                sb.append(values.getCount(value));
                sb.append(System.lineSeparator());
            }
            logsToCollapse.append(sb.toString());
        }

        // Adding the new collapsed months in past_months.txt
        // Removing the logs of these past months from the file containing the logs of the current year
        Path pathToPastMonthsValuesFile = Path.of(filePathString, PAST_MONTHS);
        Files.writeString(pathToPastMonthsValuesFile, logsToCollapse.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE);
    }

    private void updateOperationsBecauseYearChanged() throws IOException {

        int yearCurrent = LocalDateTime.now().getYear();

        currentYearLastTimeWeChecked = yearCurrent;

        Path pathToCurrentMonthLogFile = Path.of(filePathString, PAST_MONTHS);

        // Data structure to store all different log events and their counts per year
        Map<Integer, Multiset> spreadPerYear = new TreeMap();

        CsvParserSettings settings = new CsvParserSettings();
        settings.getFormat().setLineSeparator("\n");
        settings.setMaxCharsPerColumn(-1);
        CsvParser parser = new CsvParser(settings);

        List<String[]> rows = parser.parseAll(pathToCurrentMonthLogFile.toFile());
        for (String[] row : rows) {
            String[] yearAndMonth = row[0].split("_");
            String year = yearAndMonth[0];
            String month = yearAndMonth[1];
            String field = row[1];
            String count = row[2];
            try {
                Integer valueOfCount = Integer.valueOf(count);
            } catch (Exception e) {
                System.out.println("error!");
            }
            Multiset<String> logEventsForAGivenYear = spreadPerYear.getOrDefault(Integer.valueOf(year), new Multiset());
            logEventsForAGivenYear.addSeveral(field, Integer.valueOf(count));
            spreadPerYear.put(Integer.valueOf(year), logEventsForAGivenYear);
        }
        Iterator<Map.Entry<Integer, Multiset>> iteratorOnYears = spreadPerYear.entrySet().iterator();
        StringBuilder logsToCollapse = new StringBuilder();
        Path pathToPastYearsValuesFile = Path.of(filePathString, PAST_YEARS);
        while (iteratorOnYears.hasNext()) {
            Map.Entry<Integer, Multiset> nextEntry = iteratorOnYears.next();
            Multiset<String> values = nextEntry.getValue();
            StringBuilder sb = new StringBuilder();
            for (String value : values.getElementSet()) {
                sb.append(nextEntry.getKey());
                sb.append(",");
                sb.append(value);
                sb.append(",");
                sb.append(values.getCount(value));
                sb.append(System.lineSeparator());
            }
            logsToCollapse.append(sb.toString());
        }

        // Adding the new collapsed years in past.txt
        // Removing the logs of these past years from the file containing the logs of the current year
        Files.writeString(pathToPastYearsValuesFile, logsToCollapse.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        Files.delete(pathToCurrentMonthLogFile);
    }

    private void updateOperationsBecauseMonthChanged() throws IOException {

        int monthCurrent = LocalDateTime.now().getMonthValue();
        int yearCurrent = LocalDateTime.now().getYear();

        // updapte tracker of current month
        currentMonthLastTimeWeChecked = monthCurrent;

        Path pathToCurrentYearLogFile = Path.of(filePathString, CURRENT_LOG);
        Path pathToAllLogs = Path.of(filePathString, ALL_LOGS);

        // Data structure to store all different log events and their counts per month
        Map<String, Multiset> spreadPerMonth = new TreeMap();

        CsvParserSettings settings = new CsvParserSettings();
        settings.getFormat().setLineSeparator("\n");
        settings.setMaxCharsPerColumn(-1);
        CsvParser parser = new CsvParser(settings);

        if (!Files.exists(pathToAllLogs)) {
            Files.createFile(pathToAllLogs);
        }
        if (!Files.exists(pathToCurrentYearLogFile)) {
            Files.createFile(pathToCurrentYearLogFile);
        }

        List<String[]> rows = parser.parseAll(pathToCurrentYearLogFile.toFile());
        StringBuilder rowsOfNewMonthToPreserve = new StringBuilder();
        StringBuilder toVerseToAllLogs = new StringBuilder();
        for (String[] row : rows) {
            LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(row[0])), ZoneId.systemDefault());
            String event = row[1];
            if (event.startsWith("test") || userAgentsToIgnore.contains(event.toLowerCase()) || userAgentsToIgnore.contains(row[2].toLowerCase()) || userAgentsToIgnore.contains(row[3].toLowerCase())) {
                continue;
            }

            int year = localDateTime.getYear();
            int month = localDateTime.getMonthValue();
            if (year == yearCurrent && month == monthCurrent) {
                rowsOfNewMonthToPreserve.append(row[0]).append(",").append(row[1]).append(",").append(row[2]).append(",").append(row[3]).append(",").append(row[4]).append(System.lineSeparator());
            } else {
                toVerseToAllLogs.append(row[0]).append(",").append(row[1]).append(",").append(row[2]).append(",").append(row[3]).append(",").append(row[4]).append(System.lineSeparator());
                Multiset<String> logEventsForAGivenMonth = spreadPerMonth.getOrDefault(year + "_" + month, new Multiset());
                logEventsForAGivenMonth.addOne(event);
                spreadPerMonth.put(year + "_" + month, logEventsForAGivenMonth);
            }
        }
        Iterator<Map.Entry<String, Multiset>> iteratorOnMonths = spreadPerMonth.entrySet().iterator();
        StringBuilder logsToCollapse = new StringBuilder();
        Path pathToPastMonthsValuesFile = Path.of(filePathString, PAST_MONTHS);
        while (iteratorOnMonths.hasNext()) {
            Map.Entry<String, Multiset> nextEntry = iteratorOnMonths.next();
            Multiset<String> values = nextEntry.getValue();
            StringBuilder sb = new StringBuilder();
            for (String value : values.getElementSet()) {
                sb.append(nextEntry.getKey());
                sb.append(",");
                sb.append(value);
                sb.append(",");
                sb.append(values.getCount(value));
                sb.append(System.lineSeparator());
            }
            logsToCollapse.append(sb.toString());
        }

        // Adding the new collapsed months in past_months.txt
        // Removing the logs of these past months from the file containing the logs of the current year
        Files.writeString(pathToPastMonthsValuesFile, logsToCollapse.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        Files.delete(pathToCurrentYearLogFile);
        Files.writeString(pathToCurrentYearLogFile, rowsOfNewMonthToPreserve.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        Files.writeString(pathToAllLogs, toVerseToAllLogs.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public void getPrimefacesChartForLaunches() throws IOException {

        modelLaunch = new BarChartModel();

        ChartData chartData = new ChartData();

        Path pathToPastYearsValuesFile = Path.of(filePathString, PAST_YEARS);
        Path pathToPastMonthsValuesFile = Path.of(filePathString, PAST_MONTHS);

        List<String> eventsMostFrequentToLeast = new ArrayList();
        Multiset<String> eventsCountedFromPastYears = new Multiset();
        List<String> orderedAbscyssLabels = new ArrayList();
        Map<String, Map<String, Integer>> eventToCountPerYearAndCurrentMonths = new TreeMap();

        List<String> allPastYears = Files.readAllLines(pathToPastYearsValuesFile, StandardCharsets.UTF_8);
        for (String oneEventPastYears : allPastYears) {
            String[] fields = oneEventPastYears.split(",");
            String year = fields[0];
            String event = fields[1];
            String count = fields[2];
            Integer countAsInteger = Integer.valueOf(count);
            eventsCountedFromPastYears.addSeveral(event, countAsInteger);
            if (orderedAbscyssLabels.isEmpty() || !orderedAbscyssLabels.get(orderedAbscyssLabels.size() - 1).equals(year)) {
                orderedAbscyssLabels.add(year);
            }
            Map<String, Integer> countForOneYearOrMonthForOneEvent = eventToCountPerYearAndCurrentMonths.getOrDefault(event, new HashMap());
            countForOneYearOrMonthForOneEvent.put(year, countAsInteger);
            eventToCountPerYearAndCurrentMonths.put(event, countForOneYearOrMonthForOneEvent);
        }

        // adding months of current year to the labels of the absciss
        if (Files.exists(pathToPastMonthsValuesFile)) {
            List<String> allPastMonths = Files.readAllLines(pathToPastMonthsValuesFile, StandardCharsets.UTF_8);
            for (String oneEventPastMonths : allPastMonths) {
                String[] fields = oneEventPastMonths.split(",");
                String currMonth = fields[0].split("\\_")[1];
                String event = fields[1];
                String count = fields[2];
                Integer countAsInteger = Integer.valueOf(count);
                Integer monthValue = Integer.valueOf(currMonth);
                String displayNameMonth = Month.of(monthValue).getDisplayName(TextStyle.FULL, Locale.US) + " " + currentYearLastTimeWeChecked;
                if (orderedAbscyssLabels.isEmpty() || !orderedAbscyssLabels.get(orderedAbscyssLabels.size() - 1).equals(displayNameMonth)) {
                    orderedAbscyssLabels.add(displayNameMonth);
                }
                Map<String, Integer> countForOneYearOrMonthForOneEvent = eventToCountPerYearAndCurrentMonths.getOrDefault(event, new HashMap());
                countForOneYearOrMonthForOneEvent.put(displayNameMonth, countAsInteger);
                eventToCountPerYearAndCurrentMonths.put(event, countForOneYearOrMonthForOneEvent);
            }
        }

        // adding current month not aggregated yet to the abscyss values
        String displayNameMonth = Month.of(LocalDate.now().getMonthValue()).getDisplayName(TextStyle.FULL, Locale.US) + " " + currentYearLastTimeWeChecked;
        orderedAbscyssLabels.add(displayNameMonth);

        // adding current month values, not aggregated yet, to eventToCountPerYearAndCurrentMonths
        Path pathToCurrentYearLogFile = Path.of(filePathString, CURRENT_LOG);
        List<String> logsForCurrentMonth = Files.readAllLines(pathToCurrentYearLogFile);
        for (String currLog : logsForCurrentMonth) {
            String[] fields = currLog.split(",");
            String event = fields[1];
            if (event.startsWith("test") || userAgentsToIgnore.contains(event.toLowerCase()) || userAgentsToIgnore.contains(fields[2].toLowerCase()) || userAgentsToIgnore.contains(fields[3].toLowerCase())) {
                continue;
            }
            Map<String, Integer> countForCurrentMonthForOneEvent = eventToCountPerYearAndCurrentMonths.getOrDefault(event, new HashMap());
            Integer countForThisEventInCurrentMonth = countForCurrentMonthForOneEvent.getOrDefault(displayNameMonth, 0);
            countForCurrentMonthForOneEvent.put(displayNameMonth, ++countForThisEventInCurrentMonth);
            eventToCountPerYearAndCurrentMonths.put(event, countForCurrentMonthForOneEvent);
        }

        // now processing values for all events, for all years and months
        List<Map.Entry<String, Integer>> eventsSortedDecreasingly = eventsCountedFromPastYears.sortDesc(eventsCountedFromPastYears);
        for (Map.Entry<String, Integer> entry : eventsSortedDecreasingly) {
            eventsMostFrequentToLeast.add(entry.getKey());
        }

        Iterator<String> iteratorEventsCountedInPastYears = eventsMostFrequentToLeast.iterator();
        int indexSeries = 0;
        while (iteratorEventsCountedInPastYears.hasNext()) {
            String nextEvent = iteratorEventsCountedInPastYears.next();
            BarChartDataSet series = new BarChartDataSet();
            series.setLabel(nextEvent);
            series.setBackgroundColor(rgbColors.get(indexSeries));
            List<Number> seriesValues = new ArrayList();
            for (String abscissValue : orderedAbscyssLabels) {
                Map<String, Integer> yearlyCountsPlusLatestMonthsForOneEvent = eventToCountPerYearAndCurrentMonths.get(nextEvent);
                Integer countForThisEvent = yearlyCountsPlusLatestMonthsForOneEvent.getOrDefault(abscissValue, 0);
                seriesValues.add(countForThisEvent);
            }
            series.setData(seriesValues);
            chartData.addChartDataSet(series);
            indexSeries++;
            if (indexSeries >= rgbColors.size()){
                indexSeries = 0;
            }
        }

        BarChartOptions options = new BarChartOptions();
        options.setMaintainAspectRatio(false);
        Title title = new Title();
        title.setDisplay(true);
        title.setText("Count of executions for each function");

        Legend legend = new Legend();
        legend.setPosition("ne");

        options.setTitle(title);
        options.setLegend(legend);

        CartesianScales cScales = new CartesianScales();
        CartesianLinearAxes linearAxe = new CartesianLinearAxes();
        linearAxe.setStacked(true);
        linearAxe.setOffset(true);

        List<String> labelsX = new ArrayList<>();
        for (String abscissValue : orderedAbscyssLabels) {
            labelsX.add(abscissValue);
        }
        chartData.setLabels(labelsX);

        cScales.addXAxesData(linearAxe);
        cScales.addYAxesData(linearAxe);

        options.setScales(cScales);

        modelLaunch.setData(chartData);
        modelLaunch.setOptions(options);
        modelLaunch.setExtender("chartExtender");
    }

    public BarChartModel getModelLaunch() throws IOException {
        getPrimefacesChartForLaunches();
        return modelLaunch;
    }

    public void setModelLaunch(HorizontalBarChartModel modelLaunch) {
        this.modelLaunch = modelLaunch;
    }
}
