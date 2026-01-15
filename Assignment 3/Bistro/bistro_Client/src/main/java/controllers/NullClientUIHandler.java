package controllers;

import desktop_screen.DesktopScreenController;
import responses.ManagerResponse;
import responses.ReportResponse;
import responses.ReservationResponse;
import responses.SeatingResponse;
import responses.UserHistoryResponse;
import responses.WaitingListResponse;
/**
 * No-op UI handler used when no UI is attached.
 *
 * Provides safe console logging and avoids {@code NullPointerException} when
 * UI callbacks are invoked before a real handler is registered.

 */
public class NullClientUIHandler implements ClientUIHandler {
    @Override
    public void showInfo(String title, String message) {
        System.out.println("[INFO] " + title + ": " + message);
    }

    @Override
    public void showWarning(String title, String message) {
        System.out.println("[WARN] " + title + ": " + message);
    }

    @Override
    public void showError(String title, String message) {
        System.err.println("[ERROR] " + title + ": " + message);
    }

    @Override
    public void showPayload(Object payload) {
        System.out.println("[PAYLOAD] " + payload);
    }

    @Override
    public void routeToDesktop(DesktopScreenController.Role role, String username) {
        System.out.println("[ROUTE] role=" + role + " username=" + username);
    }

    @Override
    public void onReservationResponse(ReservationResponse response) {}

    @Override
    public void onSeatingResponse(SeatingResponse response) {}

    @Override
    public void onUserHistoryResponse(java.util.List<UserHistoryResponse> rows) {}

    @Override
    public void onUserHistoryError(String message) {}

    @Override
    public void onReportResponse(ReportResponse reportResponse) {}

    @Override
    public void onUpcomingReservationsResponse(java.util.List<ReservationResponse> rows) {}

    @Override
    public void onUpcomingReservationsError(String message) {}

    @Override
    public void onManagerResponse(ManagerResponse response) {}

    @Override
    public void onBillTotal(double baseTotal, boolean isCash) {}

    @Override
    public void onBillPaid(Integer tableNumber) {}

    @Override
    public void onBillError(String message) {}

    @Override
    public void onUserDetailsResponse(String email, String phone) {}
    @Override
    public void onWaitingListCancellation(WaitingListResponse response) {
        // no-op
    }
}