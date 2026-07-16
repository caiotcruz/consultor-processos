// src/app/features/profile/profile.page.ts
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle,
  IonList, IonItem, IonLabel, IonInput, IonToggle,
  IonButton, IonIcon, IonNote, AlertController
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  personOutline, mailOutline, notificationsOutline,
  keyOutline, trashOutline, logOutOutline, chevronForwardOutline,
  shieldCheckmarkOutline, speedometerOutline
} from 'ionicons/icons';
import { UserService } from '../../../services/user.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { UserProfile } from '../../../models/user.model';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle,
    IonItem, IonLabel, IonInput, IonToggle,
    IonIcon,
    LoadingSpinnerComponent
  ],
  templateUrl: './profile.page.html',
  styleUrls: ['./profile.page.scss']
})
export class ProfilePage implements OnInit {

  profile = signal<UserProfile | null>(null);
  isLoading = signal(true);
  isSavingName = false;

  nameForm = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2)]]
  });

  initials = () => {
    const name = this.profile()?.name ?? '';
    return name.split(' ').slice(0, 2).map(n => n[0]).join('').toUpperCase();
  };

  constructor(
    private fb: FormBuilder,
    private userService: UserService,
    private authService: AuthService,
    private toast: ToastService,
    private router: Router,
    private alertCtrl: AlertController
  ) {
    addIcons({
      personOutline,
      mailOutline,
      notificationsOutline,
      keyOutline,
      trashOutline,
      logOutOutline,
      chevronForwardOutline,
      shieldCheckmarkOutline,
      speedometerOutline
    });
  }

  ngOnInit(): void {
    this.userService.getProfile().subscribe({
      next: resp => {
        this.profile.set(resp.data ?? null);
        this.nameForm.patchValue({ name: resp.data?.name ?? '' });
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false)
    });
  }

  saveName(): void {
    if (this.nameForm.invalid) return;
    this.isSavingName = true;
    this.userService.updateProfile({ name: this.nameForm.value.name! }).subscribe({
      next: resp => {
        this.profile.set(resp.data ?? null);
        this.isSavingName = false;
        this.toast.success('Nome atualizado com sucesso.');
      },
      error: () => {
        this.isSavingName = false;
        this.toast.error('Erro ao salvar nome.');
      }
    });
  }

  updateNotifPref(pref: 'emailEnabled' | 'pushEnabled', value: boolean): void {
    this.userService.updateProfile({ notifications: { [pref]: value } }).subscribe({
      next: resp => this.profile.set(resp.data ?? null),
      error: () => this.toast.error('Erro ao atualizar preferências.')
    });
  }

  logout(): void {
    this.authService.logout();
  }

  async confirmDeleteAccount(): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Excluir Conta',
      message: 'Esta ação é irreversível. Digite sua senha e a palavra de confirmação para prosseguir.',
      cssClass: 'smooth-alert',
      inputs: [
        { name: 'password', type: 'password', placeholder: 'Sua senha' },
        { name: 'confirmPhrase', type: 'text', placeholder: 'Digite EXCLUIR para confirmar' }
      ],
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Excluir',
          role: 'destructive',
          handler: (data) => {
            if (data.confirmPhrase !== 'EXCLUIR') {
              this.toast.error('Digite exatamente: EXCLUIR');
              return false;
            }

            this.userService.deleteAccount(data.password, data.confirmPhrase).subscribe({
              next: () => {
                this.toast.info('Conta excluída com sucesso.');
                this.authService.logout();
              },
              error: () => {
                this.toast.error('Erro ao excluir conta. Verifique sua senha.');
              }
            });

            return true;
          }
        }
      ]
    });
    await alert.present();
  }
}