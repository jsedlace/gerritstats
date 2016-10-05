package com.holmsted.gerrit.processors;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.holmsted.gerrit.Commit;
import com.holmsted.gerrit.CommitFilter;

public abstract class CommitVisitor {

    @Nonnull
    private final CommitFilter filter;

    public CommitVisitor(@Nonnull CommitFilter filter) {
        this.filter = filter;
    }

    public void visit(@Nonnull List<Commit> commits) {
        for (Commit commit : commits) {
            if (!isIncluded(commit) || !isIncluded(commit.owner) || !isAfterStartDate(commit)) {
                continue;
            }
            visitCommit(commit);

            for (Commit.PatchSet patchSet: commit.patchSets) {
                if (!isIncluded(patchSet.author)) {
                    continue;
                }
                visitPatchSet(commit, patchSet);

                for (Commit.Approval approval : patchSet.approvals) {
                    if (!isIncluded(approval.grantedBy)) {
                        continue;
                    }
                    visitApproval(patchSet, approval);
                }

                for (Commit.PatchSetComment patchSetComment : patchSet.comments) {
                    if (!isIncluded(patchSetComment.reviewer)) {
                        continue;
                    }
                    visitPatchSetComment(commit, patchSet, patchSetComment);
                }
            }
        }
    }

    private boolean isIncluded(@Nonnull Commit commit) {
        return filter.isIncluded(commit);
    }

    private boolean isIncluded(@Nullable Commit.Identity identity) {
        return filter.isIncluded(identity);
    }

    private boolean isAfterStartDate(@Nonnull Commit commit) {
        return commit.lastUpdatedDate >= filter.getStartDateTimestamp().get();
    }

    public abstract void visitCommit(@Nonnull Commit commit);

    public abstract void visitPatchSet(@Nonnull Commit commit, @Nonnull Commit.PatchSet patchSet);

    public abstract void visitApproval(@Nonnull Commit.PatchSet patchSet, @Nonnull Commit.Approval approval);

    public abstract void visitPatchSetComment(@Nonnull Commit commit,
                                              @Nonnull Commit.PatchSet patchSet,
                                              @Nonnull Commit.PatchSetComment patchSetComment);
}
