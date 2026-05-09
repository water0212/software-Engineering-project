package com.water.fzfwificenter.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectSummaryResult {

    private String projectName;
    private List<ProjectFileSummary> files = new ArrayList<>();

    public ProjectSummaryResult() {
    }

    public ProjectSummaryResult(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public List<ProjectFileSummary> getFiles() {
        return files;
    }

    public void setFiles(List<ProjectFileSummary> files) {
        this.files = files;
    }

    public void addFile(ProjectFileSummary fileSummary) {
        this.files.add(fileSummary);
    }
}
