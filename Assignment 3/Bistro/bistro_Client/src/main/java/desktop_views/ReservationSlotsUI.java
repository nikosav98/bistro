package desktop_views;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.TilePane;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * Shared UI builder for reservation slot tiles (availability + suggestions).
 * Used by both ReservationsViewController and EditReservationViewController.
 */
public final class ReservationSlotsUI {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("dd/MM");

    private final TilePane slotsTile;
    private final Label slotInfoLabel;

    private final double slotWidth;
    private final double slotHeight;

    // selection state for slot tiles
    private Button selectedButton;
    private LocalDate selectedDate;
    private LocalTime selectedTime;

    public ReservationSlotsUI(TilePane slotsTile, Label slotInfoLabel, double slotWidth, double slotHeight) {
        this.slotsTile = slotsTile;
        this.slotInfoLabel = slotInfoLabel;
        this.slotWidth = slotWidth;
        this.slotHeight = slotHeight;
    }

    public void clear() {
        if (slotsTile != null) slotsTile.getChildren().clear();
        if (slotInfoLabel != null) slotInfoLabel.setText("");
        clearSelection();
    }

    public void info(String msg) {
        if (slotInfoLabel != null) slotInfoLabel.setText(msg == null ? "" : msg);
    }

    public void clearSelection() {
        if (selectedButton != null) {
            selectedButton.getStyleClass().remove("slot-selected");
        }
        selectedButton = null;
        selectedDate = null;
        selectedTime = null;
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    public LocalTime getSelectedTime() {
        return selectedTime;
    }

    public boolean hasSelection() {
        return selectedDate != null && selectedTime != null;
    }

    public void renderAvailability(
            LocalDate selectedDate,
            List<LocalTime> availableTimes,
            BiPredicate<LocalDate, LocalTime> exclude,
            BiConsumer<LocalDate, LocalTime> onPick
    ) {
    	if (slotsTile == null) return;

    	slotsTile.getChildren().clear();
    	clearSelection();

    	if (selectedDate == null) {
    	    info("Select a date to see available times.");
    	    return;
    	}

        if (slotsTile == null) return;

        boolean addedAny = false;

        if (availableTimes != null) {
            for (LocalTime t : availableTimes) {
                if (exclude != null && exclude.test(selectedDate, t)) continue;
                slotsTile.getChildren().add(createSlotButton(selectedDate, t, "ghost", onPick, false));
                addedAny = true;
            }
        }

        if (!addedAny) info("No available times for this date.");
    }

    public void renderSuggestions(
            Map<LocalDate, List<LocalTime>> suggestedDates,
            BiPredicate<LocalDate, LocalTime> exclude,
            BiConsumer<LocalDate, LocalTime> onPick
    ) {
        if (slotsTile == null) return;

        AtomicBoolean addedAny = new AtomicBoolean(false);

        if (suggestedDates != null && !suggestedDates.isEmpty()) {
            suggestedDates.keySet().stream().sorted().forEach(d -> {
                List<LocalTime> times = suggestedDates.get(d);
                if (times == null) return;

                for (LocalTime t : times) {
                    if (exclude != null && exclude.test(d, t)) continue;
                    slotsTile.getChildren().add(createSlotButton(d, t, "primary", onPick, false));
                    addedAny.set(true);
                }
            });
        }

        if (!addedAny.get()) info("No alternative suggestions available.");
    }

    /**
     * Selectable variant
     * - keeps an internal selected slot
     * - adds a css marker class "slot-selected"
     * - triggers a single onPick callback when selection changes
     */
    public void renderAvailabilitySelectable(
            LocalDate selectedDate,
            List<LocalTime> availableTimes,
            BiPredicate<LocalDate, LocalTime> exclude,
            BiConsumer<LocalDate, LocalTime> onPick
    ) {
        if (slotsTile == null) return;

        boolean addedAny = false;

        if (availableTimes != null) {
            for (LocalTime t : availableTimes) {
                if (exclude != null && exclude.test(selectedDate, t)) continue;
                slotsTile.getChildren().add(createSlotButton(selectedDate, t, "ghost", onPick, true));
                addedAny = true;
            }
        }

        if (!addedAny) info("No available times for this date.");
    }

    /**
     * Selectable variant for suggestions
     * - allows selecting suggested date + time
     * - keeps an internal selected slot
     */
    public void renderSuggestionsSelectable(
            Map<LocalDate, List<LocalTime>> suggestedDates,
            BiPredicate<LocalDate, LocalTime> exclude,
            BiConsumer<LocalDate, LocalTime> onPick
    ) {
        if (slotsTile == null) return;

        AtomicBoolean addedAny = new AtomicBoolean(false);

        if (suggestedDates != null && !suggestedDates.isEmpty()) {
            suggestedDates.keySet().stream().sorted().forEach(d -> {
                List<LocalTime> times = suggestedDates.get(d);
                if (times == null) return;

                for (LocalTime t : times) {
                    if (exclude != null && exclude.test(d, t)) continue;
                    slotsTile.getChildren().add(createSlotButton(d, t, "primary", onPick, true));
                    addedAny.set(true);
                }
            });
        }

        if (!addedAny.get()) info("No alternative suggestions available.");
    }

    private Button createSlotButton(
            LocalDate date,
            LocalTime time,
            String styleClass,
            BiConsumer<LocalDate, LocalTime> onPick,
            boolean selectable
    ) {
    	String text = date == null
    	        ? time.format(TIME_FMT)
    	        : time.format(TIME_FMT) + "\n" + date.format(DATE_SHORT);
        Button btn = new Button(text);

        btn.getStyleClass().addAll(styleClass, "slot-btn");
        btn.setPrefSize(slotWidth, slotHeight);
        btn.setAlignment(Pos.CENTER);
        btn.setStyle("-fx-text-alignment: center; -fx-font-size: 11px;");

        btn.setOnAction(e -> {
            if (selectable) {
                applySelection(btn, date, time);
            }
            if (onPick != null) onPick.accept(date, time);
        });

        return btn;
    }

    private void applySelection(Button btn, LocalDate date, LocalTime time) {
        if (btn == null) return;

        if (selectedButton != null) {
            selectedButton.getStyleClass().remove("slot-selected");
        }

        selectedButton = btn;
        selectedDate = date;
        selectedTime = time;

        if (!selectedButton.getStyleClass().contains("slot-selected")) {
            selectedButton.getStyleClass().add("slot-selected");
        }
    }
}
