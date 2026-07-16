import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  IonContent, IonItem, IonInput, IonText, IonSpinner, IonIcon
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { mailOutline, checkmarkCircle } from 'ionicons/icons';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonItem, IonInput, IonText, IonSpinner, IonIcon
  ],
  templateUrl: './forgot-password.page.html',
  styleUrls: ['./forgot-password.page.scss']
})
export class ForgotPasswordPage {

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });

  isLoading = false;
  sent = signal(false);
  submittedEmail = '';

  get email() { return this.form.get('email'); }

  constructor(
    private fb: FormBuilder,
    private authService: AuthService
  ) {
    addIcons({ mailOutline, checkmarkCircle });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.isLoading = true;
    this.submittedEmail = this.form.value.email!;

    this.authService.forgotPassword(this.submittedEmail).subscribe({
      next:  () => { this.isLoading = false; this.sent.set(true); },
      error: () => { this.isLoading = false; this.sent.set(true); }
    });
  }
}