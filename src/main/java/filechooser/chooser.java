
        package filechooser;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class Chooser extends Application {

    private final TextArea output = new TextArea();
    private final ProjectContext project = new ProjectContext();

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void start(Stage stage) {

        Button importFolderBtn = new Button("Import Folder");
        importFolderBtn.setOnAction(e -> chooseFolder(stage));

        Button importFilesBtn = new Button("Import Files");
        importFilesBtn.setOnAction(e -> chooseFiles(stage));

        Button saveBtn = new Button("Save Project");
        saveBtn.setOnAction(e -> saveProject());

        Button loadBtn = new Button("Load Project");
        loadBtn.setOnAction(e -> loadProject());

        output.setEditable(false);
        output.setPrefHeight(400);

        VBox root = new VBox(10, importFolderBtn, importFilesBtn, saveBtn, loadBtn, output);

        stage.setTitle("Project System");
        stage.setScene(new Scene(root, 600, 500));
        stage.show();
    }

    // =========================
    // 📁 匯入資料夾
    // =========================
    private void chooseFolder(Stage stage) {

        DirectoryChooser chooser = new DirectoryChooser();
        File folder = chooser.showDialog(stage);
        if (folder == null) return;

        FileScanner scanner = new FileScanner();
        List<File> files = scanner.scan(folder);
        importFiles(files, "Import done");
    }

    private void chooseFiles(Stage stage) {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Java Files");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Java Files", "*.java")
        );

        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) return;

        importFiles(files, "Imported files");
    }

    private void importFiles(List<File> files, String successPrefix) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (File f : files) {
                    try {
                        String content = Files.readString(f.toPath());
                        project.add(new FileNode(f.getAbsolutePath(), content));
                    } catch (Exception ignored) {}
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> output.appendText(successPrefix + ": " + project.size() + "\n"));
        new Thread(task).start();
    }

    // =========================
    // 💾 存檔（JSON）
    // =========================
    private void saveProject() {
        try {
            mapper.writeValue(
                    new File("project.json"),
                    project.getAll()
            );

            output.appendText("Saved to project.json\n");

        } catch (Exception e) {
            output.appendText("Save failed\n");
        }
    }

    // =========================
    // 📂 讀檔（JSON）
    // =========================
    private void loadProject() {
        try {
            List<FileNode> list = mapper.readValue(
                    new File("project.json"),
                    new TypeReference<List<FileNode>>() {}
            );

            project.replace(list);

            output.appendText("Loaded project: " + project.size() + "\n");

            for (FileNode n : list) {
                output.appendText(n.path + "\n");
            }

        } catch (Exception e) {
            output.appendText("Load failed\n");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    // =========================
    // 📦 Scanner
    // =========================
    static class FileScanner {

        private static final Set<String> IGNORE = Set.of(
                ".git", "node_modules", "target", "build"
        );

        public List<File> scan(File root) {
            List<File> result = new ArrayList<>();
            scanRec(root, result);
            return result;
        }

        private void scanRec(File f, List<File> out) {

            if (f.isDirectory()) {

                if (IGNORE.contains(f.getName())) return;

                File[] files = f.listFiles();
                if (files == null) return;

                for (File c : files) scanRec(c, out);

            } else if (f.getName().endsWith(".java")) {
                out.add(f);
            }
        }
    }

    // =========================
    // 📦 Model
    // =========================
    static class FileNode {
        public String path;
        public String content;

        public FileNode() {}

        public FileNode(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }

    // =========================
    // 📦 Project Memory
    // =========================
    static class ProjectContext {

        private List<FileNode> files = new ArrayList<>();

        void add(FileNode node) {
            files.add(node);
        }

        int size() {
            return files.size();
        }

        List<FileNode> getAll() {
            return files;
        }

        void replace(List<FileNode> newFiles) {
            files = newFiles;
        }
    }
}
