import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import {
  UserProfile, UpdateProfileRequest,
  ChangePasswordRequest
} from '../models/user.model';
import { NotificationHistoryItem } from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class UserService {

  private readonly url = `${environment.apiUrl}/users/me`;

  constructor(private http: HttpClient) {}

  getProfile(): Observable<ApiResponse<UserProfile>> {
    return this.http.get<ApiResponse<UserProfile>>(this.url);
  }

  updateProfile(request: UpdateProfileRequest): Observable<ApiResponse<UserProfile>> {
    return this.http.patch<ApiResponse<UserProfile>>(this.url, request);
  }

  changePassword(request: ChangePasswordRequest): Observable<ApiResponse<{ message: string }>> {
    return this.http.post<ApiResponse<{ message: string }>>(
      `${this.url}/change-password`, request
    );
  }

  deleteAccount(password: string, confirmPhrase: string):
      Observable<ApiResponse<{ message: string }>> {
    return this.http.delete<ApiResponse<{ message: string }>>(this.url, {
      body: { password, confirmPhrase }
    });
  }

  getNotificationHistory(page = 0, size = 20):
      Observable<ApiResponse<{ content: NotificationHistoryItem[] }>> {
    return this.http.get<ApiResponse<any>>(
      `${environment.apiUrl}/users/me/notifications`,
      { params: { page: String(page), size: String(size) } }
    );
  }
}