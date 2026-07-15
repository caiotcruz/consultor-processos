export interface LoginRequest {
  email:    string;
  password: string;
}

export interface RegisterRequest {
  name:     string;
  email:    string;
  password: string;
}

export interface LoginResponse {
  accessToken:  string;
  refreshToken: string;
  expiresIn:    number;
  tokenType:    string;
  user:         UserSummary;
}

export interface UserSummary {
  id:          string;
  name:        string;
  email:       string;
  plan:        string;
  planDisplay: string;
}

export interface RefreshResponse {
  accessToken:  string;
  refreshToken: string;
  expiresIn:    number;
  tokenType:    string;
}