package com.productivity.history.dto.response;

import com.productivity.attachment.entity.Attachment;
import com.productivity.attachment.entity.ReferenceLink;
import com.productivity.progress.entity.ProgressUpdate;
import com.productivity.task.entity.TaskIteration;
import java.util.List;

public record TaskHistoryDetailResponse(
        CompletedTaskResponse task,
        List<ProgressUpdate> progressUpdates,
        List<Attachment> attachments,
        List<ReferenceLink> referenceLinks,
        List<TaskIteration> iterations
) {}
