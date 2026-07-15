import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { CourtOption } from '../models/process.model';

@Injectable({ providedIn: 'root' })
export class CourtService {

  private readonly url = `${environment.apiUrl}/courts`;

  constructor(private http: HttpClient) {}

  listActive(): Observable<ApiResponse<CourtOption[]>> {
    return this.http.get<ApiResponse<CourtOption[]>>(this.url);
  }
}