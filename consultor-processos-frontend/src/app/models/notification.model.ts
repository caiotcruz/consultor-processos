export interface NotificationHistoryItem {
  id:            string;
  channel:       'EMAIL' | 'PUSH';
  eventType:     string;
  status:        'SENT' | 'FAILED' | 'SKIPPED';
  errorMessage?: string;
  sentAt:        string;
  processNumber?: string;
}