package com.github.pray.fff;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsNotifier;
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
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class MergeToTargetBranchAction extends AnAction {
    private static final Logger logger = LoggerFactory.getLogger(MergeToTargetBranchAction.class);

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
                    
                    // 使用GitOperations执行合并操作
                    GitOperations.mergeBranch("dev", targetBranch);
                    
                    // 显示成功消息
                    ApplicationManager.getApplication().invokeLater(() -> {
                        notifySuccess(project, 
                            "Merge Successful", 
                            String.format("Successfully merged dev to %s!", targetBranch));
                    });
                    
                } catch (GitOperations.GitCommandException ex) {
                    logger.warn("Merge operation failed: " + ex.getMessage());
                    
                    ApplicationManager.getApplication().invokeLater(() -> {
                        VcsNotifier.getInstance(project).notifyWarning(
                            "合并操作失败,请手动合并,当前所在分支是: " + originalBranch,
                            "失败原因是:"+ ex.getMessage()
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
            setTitle("选择目标分支");
            
            List<String> branches;
            GitRepository repository = GitUtil.getRepositoryManager(project).getRepositories().get(0);
            try {
                // 直接使用 Git4Idea API 获取分支列表
                branches = repository.getBranches().getLocalBranches().stream()
                        .map(branch -> branch.getName())
                        .collect(Collectors.toList());
            } catch (Exception e) {
                logger.error("获取分支列表失败", e);
                branches = List.of(); // 如果获取失败，使用空列表
                Messages.showErrorDialog(project, 
                    "获取分支列表失败: " + e.getMessage(), 
                    "错误");
            }
            
            branchComboBox = new ComboBox<>(branches.toArray(new String[0]));
            init(); // 初始化对话框
        }

        @Override
        protected JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(new JLabel("选择要合并到的目标分支:"), BorderLayout.NORTH);
            panel.add(branchComboBox, BorderLayout.CENTER);
            return panel;
        }

        public String getSelectedBranch() {
            return (String) branchComboBox.getSelectedItem();
        }
    }

    private void notifyError(Project project, String message) {
        VcsNotifier.getInstance(project).notifyError(
            "合并失败",
            "无法完成分支合并操作: " + message
        );
    }

    private void notifySuccess(Project project, String title, String message) {
        VcsNotifier.getInstance(project).notifySuccess(
            title,
            message
        );
    }
}