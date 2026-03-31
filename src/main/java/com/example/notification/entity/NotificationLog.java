package com.example.notification.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notification_logs")
public class NotificationLog {

	@Id
	private UUID id;

	@Column(name = "event_id")
	private String eventId;

	private String recipient;
	private String type;
	private String provider;

	@Column(name = "template_type")
	private String templateType;

	@Column(name = "template_id")
	private String templateId;

	private String status;

	@Column(columnDefinition = "TEXT")
	private String error;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}

