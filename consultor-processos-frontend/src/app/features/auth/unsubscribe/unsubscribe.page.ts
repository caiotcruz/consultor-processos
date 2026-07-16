import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import {
  IonContent, IonButton, IonSpinner, IonIcon
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { checkmarkCircleOutline, alertCircleOutline, mailOutline } from 'ionicons/icons';
import { environment } from '../../../../environments/environment';

type UnsubStatus = 'loading' | 'success' | 'error' | 'no-params';

@Component({
  selector: 'app-unsubscribe',
  standalone: true,
  imports: [CommonModule, RouterLink, IonContent, IonSpinner, IonIcon],
  templateUrl: './unsubscribe.page.html',
  styleUrls: ['./unsubscribe.page.scss']
})
export class UnsubscribePage implements OnInit {

  status = signal<UnsubStatus>('loading');

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient
  ) {
    addIcons({ checkmarkCircleOutline, alertCircleOutline, mailOutline });
  }

  ngOnInit(): void {
    const uid = this.route.snapshot.queryParamMap.get('uid');
    const sig = this.route.snapshot.queryParamMap.get('sig');

    if (!uid || !sig) {
      this.status.set('no-params');
      return;
    }

    this.http.get(
      `${environment.apiUrl}/unsubscribe`,
      { params: { uid, sig }, responseType: 'text' }
    ).subscribe({
      next:  () => this.status.set('success'),
      error: () => this.status.set('error')
    });
  }
}