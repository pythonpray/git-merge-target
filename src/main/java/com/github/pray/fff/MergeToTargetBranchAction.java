package com.github.pray.fff;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class MergeToTargetBranchAction extends AnAction {
    private static final Logger logger = LoggerFactory.getLogger(MergeToTargetBranchAction.class);
    public static final String NOTIFICATION_GROUP_ID = "MergeToTargetBranch.Notifications";

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        GitRepository repository = GitUtil.getRepositoryManager(project).getRepositories().get(0);
        
        // 初始化 GitOperations
        GitOperations.init(project, repository);
        
        // 保存当前分支名
        String currentBranch = repository.getCurrentBranchName();
        
        // 显示分支选择对话框
        BranchSelectionDialog dialog = new BranchSelectionDialog(project);
        if (dialog.showAndGet()) {
            String selectedBranch = dialog.getSelectedBranch();
            if (selectedBranch != null) {
                executeMergeTask(project, selectedBranch, currentBranch);
            }
        }
    }

    private void executeMergeTask(Project project, String targetBranch, String originalBranch) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Merging Branches") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setIndeterminate(false);
                    indicator.setText("Starting merge process...");
                    
                    GitOperations.mergeBranch(originalBranch, targetBranch);
                    
                    ApplicationManager.getApplication().invokeLater(() -> {
                        notifySuccess(project, 
                            "Merge Successful", 
                            String.format("Successfully merged %s to %s!", originalBranch, targetBranch));
                    });
                    
                } catch (GitOperations.GitCommandException ex) {
                    logger.warn("Merge operation failed: " + ex.getMessage());
                    
                    ApplicationManager.getApplication().invokeLater(() -> {
                        notifyWarning(project,
                            "Merge failed. Please resolve conflicts manually. Current branch: " + targetBranch,
                            "Failure reason: " + ex.getMessage()
                        );
                    });
                }
            }
        });
    }

    // 分支选择对话框
    private static class BranchSelectionDialog extends DialogWrapper {
        private final ComboBox<String> branchComboBox;

        public BranchSelectionDialog(Project project) {
            super(project);
            setTitle("Select Target Branch");
            
            List<String> branches;
            GitRepository repository = GitUtil.getRepositoryManager(project).getRepositories().get(0);
            try {
                branches = repository.getBranches().getLocalBranches().stream()
                        .map(branch -> branch.getName())
                        .collect(Collectors.toList());
            } catch (Exception e) {
                logger.error("Failed to get branch list", e);
                branches = List.of();
                Messages.showErrorDialog(project, 
                    "Failed to get branch list: " + e.getMessage(), 
                    "Error");
            }
            
            branchComboBox = new ComboBox<>(branches.toArray(new String[0]));
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JLabel("Select target branch to merge into:"), BorderLayout.NORTH);
            panel.add(branchComboBox, BorderLayout.CENTER);
            return panel;
        }

        public String getSelectedBranch() {
            return (String) branchComboBox.getSelectedItem();
        }
    }

    private void notifyError(Project project, String title, String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(title, message, NotificationType.ERROR)
                .notify(project);
    }

    private void notifySuccess(Project project, String title, String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(title, message, NotificationType.INFORMATION)
                .notify(project);
    }

    private void notifyWarning(Project project, String title, String message) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(title, message, NotificationType.WARNING)
                .notify(project);
    }
}