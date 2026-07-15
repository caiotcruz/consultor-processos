import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';

@Injectable({ providedIn: 'root' })
export class DeviceService {

  private readonly url = `${environment.apiUrl}/users/me/devices`;

  constructor(private http: HttpClient) {}

  registerDevice(token: string, platform: 'ANDROID' | 'IOS' | 'WEB'):
      Observable<ApiResponse<{ message: string }>> {
    return this.http.post<ApiResponse<{ message: string }>>(
      this.url, { token, platform }
    );
  }

  unregisterDevice(token: string): Observable<ApiResponse<{ message: string }>> {
    return this.http.delete<ApiResponse<{ message: string }>>(`${this.url}/${token}`);
  }
}