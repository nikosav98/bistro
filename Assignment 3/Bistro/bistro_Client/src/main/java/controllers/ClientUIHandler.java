package controllers;

import desktop_screen.DesktopScreenController;
import responses.ReservationResponse;
import responses.SeatingResponse;
import responses.WaitingListResponse;
import responses.ManagerResponse;
import responses.ReportResponse;
/**
 * UI callback contract for client controllers.
 *
 * Implementations (desktop or terminal) receive responses and status updates from
 * {@link ClientController} and render them in the appropriate UI context.
 */
public interface ClientUIHandler {
	
    void showInfo(String title, String message);
    void showWarning(String title, String message);
    void showError(String title, String message);
    void showPayload(Object payload);
    void routeToDesktop(DesktopScreenController.Role role, String username);
    void onReservationResponse(ReservationResponse response);
    void onSeatingResponse(SeatingResponse response);
    void onUserHistoryResponse(java.util.List<responses.UserHistoryResponse> rows);
    void onUserHistoryError(String message);
    void onReportResponse(ReportResponse reportResponse);
    void onUpcomingReservationsResponse(java.util.List<responses.ReservationResponse> rows);
    void onUpcomingReservationsError(String message);
    void onManagerResponse(ManagerResponse response);
    void onBillTotal(double baseTotal, boolean isCash);
    void onBillPaid(Integer tableNumber);
    void onBillError(String message);
    void onUserDetailsResponse(String email, String phone);
    void onWaitingListCancellation(WaitingListResponse response);


}
