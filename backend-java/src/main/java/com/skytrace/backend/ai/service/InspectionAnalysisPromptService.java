package com.skytrace.backend.ai.service;

import com.skytrace.backend.task.dto.InspectionTaskAnalysisContext;
import com.skytrace.backend.task.service.InspectionTaskService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class InspectionAnalysisPromptService {

    private final InspectionTaskService inspectionTaskService;

    public InspectionAnalysisPromptService(
            InspectionTaskService inspectionTaskService) {
        this.inspectionTaskService = inspectionTaskService;
    }

    public String buildPrompt(String taskCode, String question) {
        InspectionTaskAnalysisContext task =
                inspectionTaskService.findAnalysisContext(taskCode);

        return """
                你是无人机巡检业务助手。请只根据下面由业务系统从 MySQL 查询出的真实任务数据进行分析。
                如果数据不足，请明确指出缺少哪些信息，不要自行编造设备状态、时间、位置或告警。

                【真实巡检任务数据】
                任务编号：%s
                任务名称：%s
                设备编号：%s
                当前状态：%s
                计划开始时间：%s
                计划结束时间：%s
                记录创建时间：%s
                最后更新时间：%s

                【用户问题】
                %s

                请给出结论、判断依据和可执行建议，并重点考虑飞行安全、通信时延、故障恢复和操作优先级。
                """.formatted(
                task.taskCode(),
                task.taskName(),
                valueOrUnknown(task.deviceCode()),
                task.status(),
                valueOrUnknown(task.planStartTime()),
                valueOrUnknown(task.planEndTime()),
                valueOrUnknown(task.createdAt()),
                valueOrUnknown(task.updatedAt()),
                question
        );
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "未设置" : value;
    }

    private String valueOrUnknown(LocalDateTime value) {
        return value == null ? "未设置" : value.toString();
    }
}
