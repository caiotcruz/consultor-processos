export interface UserProfile {
  id:            string;
  name:          string;
  email:         string;
  status:        string;
  plan:          PlanInfo;
  usage:         UsageInfo;
  notifications: NotificationPrefs;
  createdAt:     string;
  lastLoginAt?:  string;
}

export interface PlanInfo {
  name:               string;
  displayName:        string;
  maxProcesses?:      number;
  checkIntervalHours: number;
}

export interface UsageInfo {
  activeProcesses:    number;
  remainingProcesses?: number;
}

export interface NotificationPrefs {
  emailEnabled: boolean;
  pushEnabled:  boolean;
}

export interface UpdateProfileRequest {
  name?:          string;
  notifications?: Partial<NotificationPrefs>;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword:     string;
}