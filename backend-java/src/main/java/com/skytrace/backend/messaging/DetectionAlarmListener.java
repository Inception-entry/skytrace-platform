package com.skytrace.backend.messaging;

import com.skytrace.backend.alarm.dto.AlarmResponse;
import com.skytrace.backend.alarm.dto.CreateAlarmRequest;
import com.skytrace.backend.alarm.service.AlarmCreateResult;
import com.skytrace.backend.alarm.service.AlarmService;
import com.skytrace.backend.evidence.MinioProperties;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceSourceType;
import com.skytrace.backend.evidence.service.EvidenceRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(
        name = "app.messaging.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DetectionAlarmListener {

    private static final Logger log = LoggerFactory.getLogger(
            DetectionAlarmListener.class
    );

    private final AlarmService alarmService;
    private final AlarmRealtimePublisher realtimePublisher;
    private final ObjectProvider<EvidenceRegistrationService> registrationService;
    private final ObjectProvider<MinioProperties> minioProperties;

    public DetectionAlarmListener(
            AlarmService alarmService,
            AlarmRealtimePublisher realtimePublisher,
            ObjectProvider<EvidenceRegistrationService> registrationService,
            ObjectProvider<MinioProperties> minioProperties) {
        this.alarmService = alarmService;
        this.realtimePublisher = realtimePublisher;
        this.registrationService = registrationService;
        this.minioProperties = minioProperties;
    }

    @RabbitListener(queues = MessagingProperties.DETECTION_QUEUE)
    public void onDetection(DetectionAlarmMessage message) {
        if (alarmService.existsByDetectionId(message.detectionId())) {
            log.info(
                    "event=detection_alarm_duplicate detectionId={} taskCode={}",
                    message.detectionId(),
                    message.taskCode()
            );
            return;
        }

        LocalDateTime eventTime = message.eventTime() == null
                ? LocalDateTime.now()
                : message.eventTime();

        String primaryEvidenceCode = null;
        String primaryVideoEvidenceCode = null;
        EvidenceRegistrationService registrar = registrationService.getIfAvailable();
        MinioProperties minio = minioProperties.getIfAvailable();
        if (registrar != null && minio != null) {
            try {
                if (message.imageObjectKey() != null
                        && !message.imageObjectKey().isBlank()) {
                    EvidenceAsset image = registrar.register(
                            new EvidenceRegistrationService.RegisterCommand(
                                    message.imageObjectKey().trim(),
                                    minio.getEvidenceBucket(),
                                    "image/jpeg",
                                    message.imageObjectKey(),
                                    0L,
                                    EvidenceSourceType.AI_DETECTION,
                                    message.taskCode(),
                                    null,
                                    message.deviceCode(),
                                    null,
                                    "system",
                                    "ai-detection"
                            )
                    );
                    primaryEvidenceCode = image.getEvidenceCode();
                }
                if (message.videoObjectKey() != null
                        && !message.videoObjectKey().isBlank()) {
                    EvidenceAsset video = registrar.register(
                            new EvidenceRegistrationService.RegisterCommand(
                                    message.videoObjectKey().trim(),
                                    minio.getEvidenceBucket(),
                                    "video/mp4",
                                    message.videoObjectKey(),
                                    0L,
                                    EvidenceSourceType.AI_DETECTION,
                                    message.taskCode(),
                                    null,
                                    message.deviceCode(),
                                    null,
                                    "system",
                                    "ai-detection"
                            )
                    );
                    primaryVideoEvidenceCode = video.getEvidenceCode();
                }
            } catch (RuntimeException ex) {
                // 证据登记失败不应阻断告警落库（CI：先上传再 detection）
                log.warn(
                        "event=detection_evidence_register_failed taskCode={} err={}",
                        message.taskCode(),
                        ex.toString()
                );
            }
        }

        try {
            AlarmCreateResult result = alarmService.createResult(new CreateAlarmRequest(
                    message.deviceCode(),
                    message.taskCode(),
                    message.eventType(),
                    message.weaponType(),
                    message.confidence(),
                    message.latitude(),
                    message.longitude(),
                    message.imageObjectKey(),
                    message.videoObjectKey(),
                    primaryEvidenceCode,
                    primaryVideoEvidenceCode,
                    eventTime,
                    message.detectionId()
            ), true, false);
            if (!result.inserted()) {
                log.info(
                        "event=detection_alarm_duplicate detectionId={} eventCode={}",
                        message.detectionId(),
                        result.alarm().eventCode()
                );
                return;
            }
            AlarmResponse alarm = result.alarm();
            realtimePublisher.publishCreated(alarm);
            log.info(
                    "event=detection_alarm_consumed eventCode={} taskCode={} evidence={}",
                    alarm.eventCode(),
                    alarm.taskCode(),
                    primaryEvidenceCode
            );
        } catch (DataIntegrityViolationException ex) {
            log.info(
                    "event=detection_alarm_duplicate detectionId={} taskCode={}",
                    message.detectionId(),
                    message.taskCode()
            );
        }
    }
}
