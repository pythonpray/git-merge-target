package com.github.pray.fff;

import com.intellij.openapi.project.Project;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitOperations {
    private static final Logger logger = LoggerFactory.getLogger(GitOperations.class);
    private static Project project;
    private static GitRepository repository;

    public static class GitCommandException extends Exception {
        public GitCommandException(String message) {
            super(message);
        }
    }

    public static void init(Project p, GitRepository repo) {
        project = p;
        repository = repo;
    }

    /**
     * 合并分支
     */
    public static void mergeBranch(String sourceBranch, String targetBranch) throws GitCommandException {
        if (project == null || repository == null) {
            throw new GitCommandException("Git environment not initialized");
        }

        logger.info("\n开始合并分支: 从 {} 到 {}", sourceBranch, targetBranch);

        try {
            Git git = Git.getInstance();

            // 1. 切换到目标分支
            logger.info("\n第1步: 切换到目标分支 {}", targetBranch);
            GitLineHandler checkoutHandler = new GitLineHandler(project, repository.getRoot(), GitCommand.CHECKOUT);
            checkoutHandler.addParameters(targetBranch);
            GitCommandResult checkoutResult = git.runCommand(checkoutHandler);
            if (!checkoutResult.success()) {
                throw new GitCommandException("切换到目标分支失败: " + String.join("\n", checkoutResult.getErrorOutput()));
            }

            // 2. 拉取目标分支最新代码
            logger.info("\n第2步: 拉取目标分支 {} 的最新代码", targetBranch);
            GitLineHandler pullHandler = new GitLineHandler(project, repository.getRoot(), GitCommand.PULL);
            pullHandler.addParameters("origin", targetBranch);
            GitCommandResult pullResult = git.runCommand(pullHandler);
            if (!pullResult.success()) {
                throw new GitCommandException("拉取目标分支失败: " + String.join("\n", pullResult.getErrorOutput()));
            }

            // 3. 合并源分支
            logger.info("\n第3步: 合并源分支 {}", sourceBranch);
            GitLineHandler mergeHandler = new GitLineHandler(project, repository.getRoot(), GitCommand.MERGE);
            mergeHandler.addParameters("--no-ff", sourceBranch);
            GitCommandResult mergeResult = git.runCommand(mergeHandler);
            if (!mergeResult.success()) {
                throw new GitCommandException("合并源分支失败: " + String.join("\n", mergeResult.getErrorOutput()));
            }

            // 4. 推送到远程
            logger.info("\n第4步: 推送更改到远程仓库");
            GitLineHandler pushHandler = new GitLineHandler(project, repository.getRoot(), GitCommand.PUSH);
            pushHandler.addParameters("origin", targetBranch);
            GitCommandResult pushResult = git.runCommand(pushHandler);
            if (!pushResult.success()) {
                throw new GitCommandException("推送到远程失败: " + String.join("\n", pushResult.getErrorOutput()));
            }

            // 5. 切回源分支
            logger.info("\n第5步: 切回源分支 {}", sourceBranch);
            GitLineHandler checkoutBackHandler = new GitLineHandler(project, repository.getRoot(), GitCommand.CHECKOUT);
            checkoutBackHandler.addParameters(sourceBranch);
            GitCommandResult checkoutBackResult = git.runCommand(checkoutBackHandler);
            if (!checkoutBackResult.success()) {
                logger.warn("切回源分支失败: {}", String.join("\n", checkoutBackResult.getErrorOutput()));
                // 这里我们只记录警告，不抛出异常，因为主要操作已经成功完成
            }

            logger.info("\n合并完成: {} -> {}", sourceBranch, targetBranch);
            
        } catch (Exception e) {
            logger.error("\n执行过程中发生错误: {}", e.getMessage());
            throw new GitCommandException(e.getMessage());
        }
    }
} 