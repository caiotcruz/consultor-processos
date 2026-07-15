import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { StorageService } from './storage.service';
import {
  LoginRequest, RegisterRequest, LoginResponse,
  RefreshResponse, UserSummary
} from '../../models/auth.model';
import { ApiResponse } from '../../models/api-response.model';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly apiUrl = environment.apiUrl;

  private _currentUser = signal<UserSummary | null>(null);

  readonly currentUser  = this._currentUser.asReadonly();
  readonly isLoggedIn   = computed(() => this._currentUser() !== null);
  readonly isAdmin      = computed(() => false); // roles vêm do JWT — expandir se necessário

  constructor(
    private http:    HttpClient,
    private storage: StorageService,
    private router:  Router
  ) {
    this.restoreSession();
  }

  login(request: LoginRequest): Observable<ApiResponse<LoginResponse>> {
    return this.http.post<ApiResponse<LoginResponse>>(
      `${this.apiUrl}/auth/login`, request
    ).pipe(
      tap(resp => {
        if (resp.success && resp.data) {
          this.storage.saveTokens(resp.data.accessToken, resp.data.refreshToken);
          this._currentUser.set(resp.data.user);
        }
      })
    );
  }

  register(request: RegisterRequest): Observable<ApiResponse<{ message: string }>> {
    return this.http.post<ApiResponse<{ message: string }>>(
      `${this.apiUrl}/auth/register`, request
    );
  }

  verifyEmail(token: string): Observable<ApiResponse<{ message: string }>> {
    return this.http.post<ApiResponse<{ message: string }>>(
      `${this.apiUrl}/auth/verify-email`, { token }
    );
  }

  forgotPassword(email: string): Observable<ApiResponse<{ message: string }>> {
    return this.http.post<ApiResponse<{ message: string }>>(
      `${this.apiUrl}/auth/forgot-password`, { email }
    );
  }

  resetPassword(token: string, newPassword: string): Observable<ApiResponse<{ message: string }>> {
    return this.http.post<ApiResponse<{ message: string }>>(
      `${this.apiUrl}/auth/reset-password`, { token, newPassword }
    );
  }

  resendVerification(email: string): Observable<ApiResponse<{ message: string }>> {
    return this.http.post<ApiResponse<{ message: string }>>(
      `${this.apiUrl}/auth/resend-verification`, { email }
    );
  }

  logout(): void {
    const refreshToken = this.storage.getRefreshToken();
    if (refreshToken) {
      this.http.post(`${this.apiUrl}/auth/logout`, { refreshToken }).subscribe();
    }
    this.storage.clearTokens();
    this._currentUser.set(null);
    this.router.navigate(['/login']);
  }

  getAccessToken():  string | null { return this.storage.getAccessToken();  }
  getRefreshToken(): string | null { return this.storage.getRefreshToken(); }

  refreshToken(): Observable<RefreshResponse> {
    const refreshToken = this.storage.getRefreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('Sem refresh token.'));
    }
    return this.http.post<ApiResponse<RefreshResponse>>(
      `${this.apiUrl}/auth/refresh`, { refreshToken }
    ).pipe(
      tap(resp => {
        if (resp.success && resp.data) {
          this.storage.saveTokens(resp.data.accessToken, resp.data.refreshToken);
        }
      }),
      catchError(err => {
        this.storage.clearTokens();
        this._currentUser.set(null);
        return throwError(() => err);
      })
    ) as any;
  }

  private restoreSession(): void {
    const token = this.storage.getAccessToken();
    if (!token) return;
  }
}