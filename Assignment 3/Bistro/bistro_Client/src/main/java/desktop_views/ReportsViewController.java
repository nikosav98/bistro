package desktop_views;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import responses.ReportResponse;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;

public class ReportsViewController implements ClientControllerAware {

    @FXML private ComboBox<Integer> yearPicker;
    @FXML private ComboBox<String> monthPicker;
    @FXML private Label infoLabel;

    @FXML private Pane timeChartHost;
    @FXML private Pane dailyChartHost;

    private ClientController clientController;
    private boolean connected;

    @FXML
    private void initialize() {
        // months
        if (monthPicker != null) {
            monthPicker.getItems().addAll(
                    "January", "February", "March", "April",
                    "May", "June", "July", "August",
                    "September", "October", "November", "December"
            );
        }

        // years (tune the range however you like)
        if (yearPicker != null) {
            int y = Year.now().getValue();
            yearPicker.getItems().addAll(y - 3, y - 2, y - 1, y, y + 1);
            yearPicker.setValue(y); // default current year
        }

        setInfo("Select year + month and generate report.");
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
    }

    @FXML
    private void onGenerate() {
        if (clientController == null || !connected) {
            setInfo("Not connected to server.");
            return;
        }

        Integer year = (yearPicker == null) ? null : yearPicker.getValue();
        if (year == null) {
            setInfo("Please select a year.");
            return;
        }

        String monthName = (monthPicker == null) ? null : monthPicker.getValue();
        if (monthName == null || monthName.isBlank()) {
            setInfo("Please select a month.");
            return;
        }

        Month m = parseMonth(monthName);
        if (m == null) {
            setInfo("Invalid month selection.");
            return;
        }

        YearMonth ym = YearMonth.of(year, m);

        requests.ReportRequest payload = new requests.ReportRequest(ym);
        requests.Request<requests.ReportRequest> req =
                new requests.Request<>(requests.Request.Command.REPORT_REQUEST, payload);

        clientController.sendRequest(req);
        setInfo("Requesting reports for " + ym + "...");
    }

    private Month parseMonth(String monthName) {
        return switch (monthName) {
            case "January" -> Month.JANUARY;
            case "February" -> Month.FEBRUARY;
            case "March" -> Month.MARCH;
            case "April" -> Month.APRIL;
            case "May" -> Month.MAY;
            case "June" -> Month.JUNE;
            case "July" -> Month.JULY;
            case "August" -> Month.AUGUST;
            case "September" -> Month.SEPTEMBER;
            case "October" -> Month.OCTOBER;
            case "November" -> Month.NOVEMBER;
            case "December" -> Month.DECEMBER;
            default -> null;
        };
    }

    public void onReportResponse(ReportResponse report) {
        if (report == null) {
            setInfo("Empty report response.");
            return;
        }

        Platform.runLater(() -> {
            try {
                renderTimeChart(report.getVisits());
                renderDailyChart(report.getMonth(), report.getDailyCounts());
                setInfo("Report loaded for " + report.getMonth());
            } catch (Exception e) {
                setInfo("Failed to render report: " + e.getMessage());
            }
        });
    }

    private void renderTimeChart(List<LocalDateTime[]> visits) {
        if (timeChartHost == null) return;

        final int openFrom = 10;
        final int openTo = 23;
        final int len = openTo - openFrom + 1;

        int[] arrivals = new int[len];
        int[] departures = new int[len];

        if (visits != null) {
            for (LocalDateTime[] v : visits) {
                if (v == null || v.length < 2) continue;
                LocalDateTime in = v[0];
                LocalDateTime out = v[1];
                if (in == null || out == null) continue;

                int inHour = in.getHour();
                int outHour = out.getHour();

                if (inHour >= openFrom && inHour <= openTo) arrivals[inHour - openFrom]++;
                if (outHour >= openFrom && outHour <= openTo) departures[outHour - openFrom]++;
            }
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Hour");

        NumberAxis yAxis = new NumberAxis(0,50,5);
        yAxis.setLabel("People");
        yAxis.setAutoRanging(false);

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(true);

        XYChart.Series<String, Number> sArr = new XYChart.Series<>();
        sArr.setName("Arrivals");

        XYChart.Series<String, Number> sDep = new XYChart.Series<>();
        sDep.setName("Departures");

        for (int h = openFrom; h <= openTo; h++) {
            String label = String.format("%02d:00", h);
            sArr.getData().add(new XYChart.Data<>(label, arrivals[h - openFrom]));
            sDep.getData().add(new XYChart.Data<>(label, departures[h - openFrom]));
        }

        chart.getData().setAll(sArr, sDep);
     
        chart.applyCss();
        chart.layout();

        
        sArr.getNode().lookup(".chart-series-line")
                .setStyle("-fx-stroke: #2ecc71; -fx-stroke-width: 3px;");

       
        sDep.getNode().lookup(".chart-series-line")
                .setStyle("-fx-stroke: #e74c3c; -fx-stroke-width: 3px;");


        chart.prefWidthProperty().bind(timeChartHost.widthProperty());
        chart.prefHeightProperty().bind(timeChartHost.heightProperty());

        timeChartHost.getChildren().setAll(chart);
    }

    private void renderDailyChart(String monthKey, Integer[][] dailyCounts) {
        if (dailyChartHost == null) return;

        int daysInMonth;
        try {
            daysInMonth = YearMonth.parse(monthKey).lengthOfMonth();
        } catch (Exception e) {
            daysInMonth = 31; // fallback
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Day");

        NumberAxis yAxis = new NumberAxis( 0,50,5);
        yAxis.setLabel("People");
        yAxis.setAutoRanging(false);

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(true);

        XYChart.Series<String, Number> sRes = new XYChart.Series<>();
        sRes.setName("Reservations");
        
        XYChart.Series<String, Number> sWait = new XYChart.Series<>();
        
        sWait.setName("Waiting List");
        for (int day = 1; day <= daysInMonth; day++) {
            int i = day - 1;

            int reservations = 0;
            int waiting = 0;

            if (dailyCounts != null && i < dailyCounts.length && dailyCounts[i] != null) {
                Integer r = dailyCounts[i].length > 0 ? dailyCounts[i][0] : null;
                Integer w = dailyCounts[i].length > 1 ? dailyCounts[i][1] : null;
                reservations = (r != null) ? r : 0;
                waiting = (w != null) ? w : 0;
            }

            String label = String.valueOf(day);
            sRes.getData().add(new XYChart.Data<>(label, reservations));
            sWait.getData().add(new XYChart.Data<>(label, waiting));
        }

        chart.getData().setAll(sRes, sWait);

        chart.prefWidthProperty().bind(dailyChartHost.widthProperty());
        chart.prefHeightProperty().bind(dailyChartHost.heightProperty());

        dailyChartHost.getChildren().setAll(chart);
    }

    private void setInfo(String msg) {
        if (infoLabel != null) infoLabel.setText(msg == null ? "" : msg);
    }
}
