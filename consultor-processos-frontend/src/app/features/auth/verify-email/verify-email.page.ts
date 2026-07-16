import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import {
  IonContent, IonButton, IonSpinner, IonIcon
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { checkmarkCircleOutline, alertCircleOutline, mailOutline } from 'ionicons/icons';
import { AuthService } from '../../../core/services/auth.service';

type VerifyStatus = 'loading' | 'success' | 'error' | 'no-token';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule, RouterLink, IonContent, IonSpinner, IonIcon],
  templateUrl: './verify-email.page.html',
  styleUrls: ['./verify-email.page.scss']
})
export class VerifyEmailPage implements OnInit {

  status = signal<VerifyStatus>('loading');

  constructor(
    private route: ActivatedRoute,
    private authService: AuthService
  ) {
    addIcons({ checkmarkCircleOutline, alertCircleOutline, mailOutline });
  }

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');

    if (!token) {
      this.status.set('no-token');
      return;
    }

    this.authService.verifyEmail(token).subscribe({
      next:  () => this.status.set('success'),
      error: () => this.status.set('error')
    });
  }
}