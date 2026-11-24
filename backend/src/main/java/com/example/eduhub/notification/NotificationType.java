package com.example.eduhub.notification;

enum NotificationType {
  ENROLLMENT_CONFIRMED {
    @Override
    String messageFor(Long sessionId) {
      return "회차 " + sessionId + " 수강 신청이 확정되었습니다.";
    }
  },
  WAITING_PROMOTED {
    @Override
    String messageFor(Long sessionId) {
      return "회차 " + sessionId + " 대기에서 수강 확정으로 승급되었습니다.";
    }
  };

  abstract String messageFor(Long sessionId);
}
