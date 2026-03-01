//package lv.lenc;
//import javafx.application.Platform;
//import javafx.scene.Scene;
//import javafx.stage.Stage;
//
//public class BackgroundGridOpener {
//
//    public static void openInNewThreadedWindow() {
//        new Thread(() -> {
//            // Эмуляция фоновой работы (например, загрузка чего-то)
//            try {
//                Thread.sleep(1000); // Пауза 1 секунда — как будто что-то грузим
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//
//            // После "загрузки" — открываем окно в JavaFX UI потоке
//            Platform.runLater(() -> {
//                BackgroundGridLayer layer = new BackgroundGridLayer();
//                Scene scene = new Scene(layer, 1280, 720); // размер можно изменить
//                Stage stage = new Stage();
//                stage.setScene(scene);
//                stage.setTitle("Background Grid Layer — Separate Window");
//                stage.show();
//            });
//        }).start();
//    }
//}
