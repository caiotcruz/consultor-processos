import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
  IonItem, IonInput, IonButton, IonText, IonSpinner, IonIcon
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { lockClosedOutline, eye, eyeOff, chevronBackOutline } from 'ionicons/icons';
import { UserService } from '../../../services/user.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
    IonItem, IonInput, IonText, IonSpinner, IonIcon
  ],
  templateUrl: './change-password.page.html',
  styleUrls: ['./change-password.page.scss']
})
export class ChangePasswordPage {

  form = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/.*\d.*/)]],
    confirmPassword: ['', Validators.required]
  }, { validators: this.matchValidator });

  show = { current: false, new: false, confirm: false };
  isLoading = false;
  errorMessage = '';

  get current() { return this.form.get('currentPassword'); }
  get newPwd() { return this.form.get('newPassword'); }
  get confirmPwd() { return this.form.get('confirmPassword'); }

  constructor(
    private fb: FormBuilder,
    private userService: UserService,
    private toast: ToastService,
    private router: Router
  ) {
    addIcons({ lockClosedOutline, eye, eyeOff, chevronBackOutline });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.isLoading = true;
    this.errorMessage = '';

    this.userService.changePassword({
      currentPassword: this.form.value.currentPassword!,
      newPassword: this.form.value.newPassword!
    }).subscribe({
      next: () => {
        this.isLoading = false;
        this.toast.success('Senha alterada com sucesso!');
        this.router.navigate(['/profile']);
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.error?.code === 'INVALID_CURRENT_PASSWORD'
            ? 'Senha atual incorreta.'
            : 'Erro ao alterar senha. Tente novamente.';
      }
    });
  }

  private matchValidator(g: AbstractControl) {
    const a = g.get('newPassword')?.value;
    const b = g.get('confirmPassword')?.value;
    return a === b ? null : { mismatch: true };
  }
}