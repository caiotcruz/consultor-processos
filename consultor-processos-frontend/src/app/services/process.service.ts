import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import {
  ProcessSummary, ProcessDetail,
  ProcessHistoryEntry, CreateProcessRequest
} from '../models/process.model';

@Injectable({ providedIn: 'root' })
export class ProcessService {

  private readonly url = `${environment.apiUrl}/processes`;

  constructor(private http: HttpClient) {}

  list(params?: { active?: boolean; status?: string; page?: number; size?: number }):
     Observable<ApiResponse<ProcessSummary[]>> {
    let httpParams = new HttpParams();
    if (params?.active  != null)   httpParams = httpParams.set('active', String(params.active));
    if (params?.status)            httpParams = httpParams.set('status', params.status);
    if (params?.page    != null)   httpParams = httpParams.set('page',   String(params.page));
    if (params?.size    != null)   httpParams = httpParams.set('size',   String(params.size));
    return this.http.get<ApiResponse<any>>(this.url, { params: httpParams });
  }

  getById(subscriptionId: string): Observable<ApiResponse<ProcessDetail>> {
    return this.http.get<ApiResponse<ProcessDetail>>(`${this.url}/${subscriptionId}`);
  }

  create(request: CreateProcessRequest): Observable<ApiResponse<ProcessDetail>> {
    return this.http.post<ApiResponse<ProcessDetail>>(this.url, request);
  }

  updateAlias(subscriptionId: string, alias: string | null):
      Observable<ApiResponse<ProcessDetail>> {
    return this.http.patch<ApiResponse<ProcessDetail>>(
      `${this.url}/${subscriptionId}`, { alias }
    );
  }

  deactivate(subscriptionId: string): Observable<ApiResponse<ProcessDetail>> {
    return this.http.post<ApiResponse<ProcessDetail>>(
      `${this.url}/${subscriptionId}/deactivate`, {}
    );
  }

  reactivate(subscriptionId: string): Observable<ApiResponse<ProcessDetail>> {
    return this.http.post<ApiResponse<ProcessDetail>>(
      `${this.url}/${subscriptionId}/reactivate`, {}
    );
  }

  delete(subscriptionId: string): Observable<ApiResponse<{ message: string }>> {
    return this.http.delete<ApiResponse<{ message: string }>>(`${this.url}/${subscriptionId}`);
  }

  getHistory( subscriptionId: string, page = 0, size = 20): 
  Observable<ApiResponse<ProcessHistoryEntry[]>> {
    const params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));

    return this.http.get<ApiResponse<ProcessHistoryEntry[]>>(
      `${this.url}/${subscriptionId}/history`,
      { params }
    );
  }
}