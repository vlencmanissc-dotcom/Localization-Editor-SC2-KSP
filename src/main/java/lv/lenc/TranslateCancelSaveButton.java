package lv.lenc;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import lv.lenc.LocalizationManager;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TranslateCancelSaveButton extends StackPane {

    public enum State { TRANSLATE, CANCEL, SAVE }

    private final LocalizationManager localization;
    private final MyButton button; // ВОТ он, твой стилизованный

    private final ObjectProperty<State> state = new SimpleObjectProperty<>(State.TRANSLATE);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private CompletableFuture<?> running;

    private Supplier<CompletableFuture<?>> translateStarter;
    private Runnable saveAction = () -> {};
    private Runnable cancelHook = () -> {};
    private Consumer<Throwable> errorHandler = Throwable::printStackTrace;
    private volatile Thread runningThread;
    public TranslateCancelSaveButton(LocalizationManager localization,
                                     String texturePath,
                                     boolean isGreen,
                                     double strengthGlow,
                                     double strengthGlowMAX) {

        this.localization = Objects.requireNonNull(localization, "localization");

        this.button = UIElementFactory.createCustomLongButton(
                localization.get("button.translate"),
                texturePath,
                isGreen,
                strengthGlow,
                strengthGlowMAX
        );

        // чтобы translateRU.setDisable(true) отключал реальную кнопку
        button.disableProperty().bind(disableProperty());

        getChildren().add(button);
        StackPane.setAlignment(button, Pos.CENTER_RIGHT);
        setMaxWidth(Double.MAX_VALUE);
        state.addListener((obs, oldS, newS) -> applyText(newS));
        applyText(State.TRANSLATE);
        state.set(State.TRANSLATE);

        button.setOnAction(e -> handleClick());
    }



    private void applyText(State s) {
        switch (s) {
            case TRANSLATE -> button.setText(localization.get("button.translate"));
            case CANCEL    -> button.setText(localization.get("button.translate.cancel"));
            case SAVE      -> button.setText(localization.get("button.translate.save"));
        }
    }

    public void refreshText() {
        applyText(state.get());
    }

    public void setTranslateStarter(Supplier<CompletableFuture<?>> starter) {
        this.translateStarter = Objects.requireNonNull(starter);
    }

    public void setSaveAction(Runnable saveAction) {
        this.saveAction = Objects.requireNonNull(saveAction);
    }

    public void setCancelHook(Runnable cancelHook) {
        this.cancelHook = Objects.requireNonNull(cancelHook);
    }

    public void setErrorHandler(Consumer<Throwable> errorHandler) {
        this.errorHandler = Objects.requireNonNull(errorHandler);
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    private void handleClick() {
        switch (state.get()) {
            case TRANSLATE -> startTranslate();
            case CANCEL    -> cancel();
            case SAVE      -> save();
        }
    }

    private void startTranslate() {
        if (translateStarter == null) {
            errorHandler.accept(new IllegalStateException("Translate starter is not set"));
            return;
        }

        cancelRequested.set(false);
        state.set(State.CANCEL);
      //  button.setDisable(false); // на всякий

        try {
            running = translateStarter.get();
            if (running == null) {
                state.set(State.TRANSLATE);
                errorHandler.accept(new IllegalStateException("Translate starter returned null future"));
                return;
            }

            running.whenComplete((ok, ex) -> Platform.runLater(() -> {
                if (cancelRequested.get()) {
                    state.set(State.TRANSLATE);
                    return;
                }
                if (ex != null) {
                    state.set(State.TRANSLATE);
                    errorHandler.accept(ex);
                    return;
                }
                state.set(State.SAVE);
            }));

        } catch (Throwable t) {
            state.set(State.TRANSLATE);
            errorHandler.accept(t);
        }
    }

    private void cancel() {
        cancelRequested.set(true);
        try { cancelHook.run(); } catch (Throwable ignored) {}

        if (runningThread != null) {
            runningThread.interrupt();
            runningThread = null;
        }

        if (running != null) {
            running.cancel(true);
            running = null;
        }

        state.set(State.TRANSLATE);
    }
    public void requestCancel() {
        Platform.runLater(this::cancel);
    }


    private void save() {
        try {
            saveAction.run();
        } catch (Throwable t) {
            errorHandler.accept(t);
        } finally {
            state.set(State.TRANSLATE);
        }
    }

    public static CompletableFuture<Void> runAsync(Runnable work) {
        return CompletableFuture.runAsync(work);
    }
}
