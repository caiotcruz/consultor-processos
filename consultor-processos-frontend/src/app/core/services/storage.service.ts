import { Injectable } from '@angular/core';

const KEYS = {
  ACCESS_TOKEN:  'cp_access_token',
  REFRESH_TOKEN: 'cp_refresh_token',
  USER:          'cp_user'
} as const;

@Injectable({ providedIn: 'root' })
export class StorageService {

  get(key: string): string | null {
    return localStorage.getItem(key);
  }

  set(key: string, value: string): void {
    localStorage.setItem(key, value);
  }

  remove(key: string): void {
    localStorage.removeItem(key);
  }

  clear(): void {
    localStorage.clear();
  }

  getAccessToken():  string | null { return this.get(KEYS.ACCESS_TOKEN);  }
  getRefreshToken(): string | null { return this.get(KEYS.REFRESH_TOKEN); }

  saveTokens(accessToken: string, refreshToken: string): void {
    this.set(KEYS.ACCESS_TOKEN,  accessToken);
    this.set(KEYS.REFRESH_TOKEN, refreshToken);
  }

  clearTokens(): void {
    this.remove(KEYS.ACCESS_TOKEN);
    this.remove(KEYS.REFRESH_TOKEN);
    this.remove(KEYS.USER);
  }
}