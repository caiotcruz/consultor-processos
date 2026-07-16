// src/app/features/auth/login/login.page.ts
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import {
  IonContent, IonItem, IonInput, IonButton,
  IonText, IonSpinner, IonIcon
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { lockClosed, mail, eye, eyeOff, scale } from 'ionicons/icons';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonItem, IonInput, IonText, IonSpinner, IonIcon
  ],
  templateUrl: './login.page.html',
  styleUrls: ['./login.page.scss']
})
export class LoginPage {

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  isLoading = false;
  showPassword = false;
  errorMessage = '';

  get email() { return this.form.get('email'); }
  get password() { return this.form.get('password'); }

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private toast: ToastService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    addIcons({ lockClosed, mail, eye, eyeOff, scale });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login(this.form.value as any).subscribe({
      next: resp => {
        this.isLoading = false;
        if (resp.success) {
          const returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/processes';
          this.router.navigate([returnUrl]);
        }
      },
      error: err => {
        this.isLoading = false;
        const code = err.error?.error?.code;
        if (code === 'INVALID_CREDENTIALS') {
          this.errorMessage = 'E-mail ou senha incorretos.';
        } else if (code === 'ACCOUNT_LOCKED') {
          this.errorMessage = 'Conta temporariamente bloqueada. Tente novamente mais tarde.';
        } else if (code === 'EMAIL_NOT_VERIFIED') {
          this.errorMessage = 'Verifique seu e-mail antes de fazer login.';
        } else {
          this.errorMessage = 'Erro ao fazer login. Tente novamente.';
        }
      }
    });
  }
}