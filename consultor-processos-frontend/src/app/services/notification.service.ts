import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, from, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { NotificationHistoryItem } from '../models/notification.model';
import { DeviceService } from './device.service';

export type PushPermissionState = 'default' | 'granted' | 'denied' | 'unsupported';

@Injectable({ providedIn: 'root' })
export class NotificationService {

  private readonly historyUrl = `${environment.apiUrl}/users/me/notifications`;

  readonly pushPermission = signal<PushPermissionState>(this.detectPermission());

  constructor(
    private http:          HttpClient,
    private deviceService: DeviceService
  ) {}

  getHistory(page = 0, size = 20):
      Observable<ApiResponse<{ content: NotificationHistoryItem[]; totalElements: number }>> {
    return this.http.get<ApiResponse<any>>(this.historyUrl, {
      params: { page: String(page), size: String(size) }
    });
  }

  requestPushPermission(): Observable<'granted' | 'denied' | 'unsupported'> {
    if (!this.isPushSupported()) {
      this.pushPermission.set('unsupported');
      return of('unsupported');
    }

    return from(Notification.requestPermission()).pipe(
      switchMap(permission => {
        this.pushPermission.set(permission as PushPermissionState);

        if (permission !== 'granted') {
          return of(permission as 'granted' | 'denied');
        }

        return this.registerFcmToken().pipe(
          catchError(() => of('granted' as const))
        );
      })
    );
  }

  isPushSupported(): boolean {
    return 'Notification' in window && 'serviceWorker' in navigator;
  }

  private detectPermission(): PushPermissionState {
    if (!this.isPushSupported()) return 'unsupported';
    return (Notification.permission as PushPermissionState) ?? 'default';
  }

  private registerFcmToken(): Observable<any> {
    return of(null);
  }
}