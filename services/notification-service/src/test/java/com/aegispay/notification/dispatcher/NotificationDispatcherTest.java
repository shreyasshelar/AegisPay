package com.aegispay.notification.dispatcher;

import com.aegispay.common.domain.enums.NotificationType;
import com.aegispay.notification.adapter.NotificationAdapter;
import com.aegispay.notification.domain.document.Notification;
import com.aegispay.notification.repository.NotificationRepository;
import com.aegispay.notification.template.NotificationTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock NotificationRepository notificationRepository;

    NotificationTemplateService templateService = new NotificationTemplateService();
    NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(templateService, messagingTemplate,
                notificationRepository, List.of());
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void dispatch_websocket_pushes_and_marks_sent() {
        dispatcher.dispatch("user-1", NotificationType.USER_REGISTERED, "WEBSOCKET", null, Map.of());

        verify(messagingTemplate).convertAndSendToUser(eq("user-1"), eq("/queue/notifications"), any());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        Notification last = captor.getAllValues().get(1);
        assertThat(last.getStatus()).isEqualTo("SENT");
        assertThat(last.getSentAt()).isNotNull();
    }

    @Test
    void dispatch_email_calls_adapter_and_persists_sent_status() {
        NotificationAdapter emailAdapter = mock(NotificationAdapter.class);
        when(emailAdapter.channel()).thenReturn("EMAIL");

        dispatcher = new NotificationDispatcher(templateService, messagingTemplate,
                notificationRepository, List.of(emailAdapter));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch("user-1", NotificationType.TRANSACTION_COMPLETED, "EMAIL",
                "user@example.com",
                Map.of("amount", "100", "currency", "USD", "externalReference", "EXT-1"));

        verify(emailAdapter).send(eq("user@example.com"), anyString(), anyString());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("SENT");
    }

    @Test
    void dispatch_adapter_failure_marks_notification_failed() {
        NotificationAdapter failingAdapter = mock(NotificationAdapter.class);
        when(failingAdapter.channel()).thenReturn("EMAIL");
        doThrow(new RuntimeException("SMTP down")).when(failingAdapter).send(any(), any(), any());

        dispatcher = new NotificationDispatcher(templateService, messagingTemplate,
                notificationRepository, List.of(failingAdapter));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch("user-1", NotificationType.TRANSACTION_FAILED, "EMAIL",
                "user@example.com", Map.of("failureReason", "NSF"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("FAILED");
        assertThat(captor.getAllValues().get(1).getErrorMessage()).contains("SMTP down");
    }
}
