import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import {
  IonContent, IonItem, IonInput, IonButton, IonText, IonSpinner, IonIcon
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { lockClosedOutline, eye, eyeOff, alertCircleOutline } from 'ionicons/icons';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonItem, IonInput, IonText, IonSpinner, IonIcon
  ],
  templateUrl: './reset-password.page.html',
  styleUrls: ['./reset-password.page.scss']
})
export class ResetPasswordPage implements OnInit {

  form = this.fb.group({
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/.*\d.*/)]],
    confirmPassword: ['', Validators.required]
  }, { validators: this.passwordMatchValidator });

  isLoading = false;
  showPassword = false;
  errorMessage = '';
  tokenFound = signal(false);
  private token = '';

  get newPassword() { return this.form.get('newPassword'); }
  get confirmPassword() { return this.form.get('confirmPassword'); }

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private toast: ToastService
  ) {
    addIcons({ lockClosedOutline, eye, eyeOff, alertCircleOutline });
  }

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (token) {
      this.token = token;
      this.tokenFound.set(true);
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.isLoading = true;
    this.errorMessage = '';

    this.authService.resetPassword(this.token, this.form.value.newPassword!).subscribe({
      next: () => {
        this.isLoading = false;
        this.toast.success('Senha redefinida com sucesso!');
        this.router.navigate(['/login']);
      },
      error: err => {
        this.isLoading = false;
        const code = err.error?.error?.code;
        this.errorMessage = code === 'TOKEN_EXPIRED'
            ? 'Link expirado. Solicite uma nova redefinição.'
            : 'Erro ao redefinir senha. Tente novamente.';
      }
    });
  }

  private passwordMatchValidator(g: AbstractControl) {
    const pw = g.get('newPassword')?.value;
    const cpw = g.get('confirmPassword')?.value;
    return pw === cpw ? null : { mismatch: true };
  }
}