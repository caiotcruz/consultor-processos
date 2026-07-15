export type ProcessStatus = 'PENDING' | 'OK' | 'ERROR' | 'BLOCKED';

export interface ProcessSummary {
  subscriptionId:   string;
  processId:        string;
  processNumber:    string;
  alias?:           string;
  court:            CourtInfo;
  status:           ProcessStatus;
  active:           boolean;
  lastCheckedAt?:   string;
  lastMovementAt?:  string;
  lastMovementDesc?: string;
  createdAt:        string;
}

export interface ProcessDetail extends ProcessSummary {
  consecutiveErrors: number;
  deactivatedAt?:    string;
}

export interface ProcessHistoryEntry {
  id:           string;
  description:  string;
  movementDate?: string;
  detectedAt:   string;
}

export interface CourtInfo {
  code: string;
  name: string;
  healthScore?: number;
}

export interface CourtOption {
  id:          string;
  code:        string;
  name:        string;
  active:      boolean;
  healthScore: number;
}

export interface CreateProcessRequest {
  processNumber: string;
  courtCode:     string;
  alias?:        string;
}