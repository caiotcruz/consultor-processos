// src/app/features/auth/register/register.page.ts
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  IonContent, IonItem, IonInput, IonButton,
  IonText, IonSpinner, IonIcon
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { personOutline, mailOutline, lockClosedOutline, mailOpenOutline } from 'ionicons/icons';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonItem, IonInput, IonText, IonSpinner, IonIcon
  ],
  templateUrl: './register.page.html',
  styleUrls: ['./register.page.scss']
})
export class RegisterPage {

  form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/.*\d.*/)]],
  });

  isLoading = false;
  registered = false;
  registeredEmail = '';
  errorMessage = '';

  get name() { return this.form.get('name'); }
  get email() { return this.form.get('email'); }
  get password() { return this.form.get('password'); }

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private toast: ToastService
  ) {
    addIcons({ personOutline, mailOutline, lockClosedOutline, mailOpenOutline });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.isLoading = true;
    this.errorMessage = '';

    this.authService.register(this.form.value as any).subscribe({
      next: resp => {
        this.isLoading = false;
        this.registered = true;
        this.registeredEmail = this.form.value.email!;
      },
      error: err => {
        this.isLoading = false;
        const code = err.error?.error?.code;
        this.errorMessage = code === 'EMAIL_ALREADY_EXISTS'
            ? 'Este e-mail já está cadastrado.'
            : 'Erro ao criar conta. Tente novamente.';
      }
    });
  }
}