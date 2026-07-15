import { Component, OnInit, signal } from '@angular/core';
import { CommonModule }      from '@angular/common';
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
  keyOutline, trashOutline, logOutOutline, chevronForwardOutline
} from 'ionicons/icons';
import { UserService }   from '../../../services/user.service';
import { AuthService }   from '../../../core/services/auth.service';
import { ToastService }  from '../../../core/services/toast.service';
import { UserProfile }   from '../../../models/user.model';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector:   'app-profile',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle,
    IonList, IonItem, IonLabel, IonInput, IonToggle,
    IonButton, IonIcon, IonNote,
    LoadingSpinnerComponent
  ],
  template: `
    <ion-header>
      <ion-toolbar color="primary">
        <ion-title>Meu Perfil</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content>
      <app-loading-spinner *ngIf="isLoading()"></app-loading-spinner>

      <div *ngIf="!isLoading() && profile()">

        <div class="profile-header">
          <div class="avatar">{{ initials() }}</div>
          <h2>{{ profile()!.name }}</h2>
          <p>{{ profile()!.email }}</p>
          <ion-note>Plano: {{ profile()!.plan.displayName }}</ion-note>
        </div>

        <div class="section-divider">Informações Pessoais</div>
        <form [formGroup]="nameForm" (ngSubmit)="saveName()">
          <ion-item>
            <ion-icon name="person-outline" slot="start" color="medium"></ion-icon>
            <ion-input label="Nome" formControlName="name" type="text"></ion-input>
            <ion-button slot="end" type="submit" fill="clear"
              [disabled]="nameForm.invalid || isSavingName">
              Salvar
            </ion-button>
          </ion-item>
        </form>

        <div class="section-divider">Uso do Plano</div>
        <ion-item>
          <ion-label>
            <p>Processos monitorados</p>
            <h3>{{ profile()!.usage.activeProcesses }}
              <span *ngIf="profile()!.usage.remainingProcesses != null">
                / {{ profile()!.plan.maxProcesses }}
              </span>
            </h3>
          </ion-label>
        </ion-item>
        <ion-item>
          <ion-label>
            <p>Intervalo de verificação</p>
            <h3>A cada {{ profile()!.plan.checkIntervalHours }}h</h3>
          </ion-label>
        </ion-item>

        <div class="section-divider">Notificações</div>
        <ion-item>
          <ion-icon name="notifications-outline" slot="start" color="medium"></ion-icon>
          <ion-label>Notificações por E-mail</ion-label>
          <ion-toggle
            [checked]="profile()!.notifications.emailEnabled"
            (ionChange)="updateNotifPref('emailEnabled', $event.detail.checked)">
          </ion-toggle>
        </ion-item>
        <ion-item>
          <ion-icon name="notifications-outline" slot="start" color="medium"></ion-icon>
          <ion-label>Notificações Push</ion-label>
          <ion-toggle
            [checked]="profile()!.notifications.pushEnabled"
            (ionChange)="updateNotifPref('pushEnabled', $event.detail.checked)">
          </ion-toggle>
        </ion-item>

        <div class="section-divider">Conta</div>
        <ion-list>
          <ion-item button routerLink="/profile/change-password" detail>
            <ion-icon name="key-outline" slot="start" color="medium"></ion-icon>
            <ion-label>Trocar Senha</ion-label>
          </ion-item>
          <ion-item button routerLink="/profile/notifications" detail>
            <ion-icon name="notifications-outline" slot="start" color="medium"></ion-icon>
            <ion-label>Histórico de Notificações</ion-label>
          </ion-item>
          <ion-item button (click)="logout()" lines="none">
            <ion-icon name="log-out-outline" slot="start" color="medium"></ion-icon>
            <ion-label>Sair</ion-label>
          </ion-item>
        </ion-list>

        <div class="section-divider" style="color: var(--ion-color-danger)">Zona de Perigo</div>
        <ion-button expand="block" fill="outline" color="danger"
          class="ion-margin" (click)="confirmDeleteAccount()">
          <ion-icon name="trash-outline" slot="start"></ion-icon>
          Excluir Conta Permanentemente
        </ion-button>

      </div>
    </ion-content>
  `,
  styles: [`
    .profile-header {
      display: flex; flex-direction: column; align-items: center;
      padding: 32px 16px 16px; gap: 4px;
    }
    .avatar {
      width: 72px; height: 72px; border-radius: 50%;
      background: var(--ion-color-primary); color: white;
      display: flex; align-items: center; justify-content: center;
      font-size: 28px; font-weight: 700; margin-bottom: 8px;
    }
    .profile-header h2 { font-size: 20px; font-weight: 700; margin: 0; }
    .profile-header p  { color: #666; font-size: 14px; margin: 0; }
  `]
})
export class ProfilePage implements OnInit {

  profile      = signal<UserProfile | null>(null);
  isLoading    = signal(true);
  isSavingName = false;

  nameForm = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2)]]
  });

  initials = () => {
    const name = this.profile()?.name ?? '';
    return name.split(' ').slice(0, 2).map(n => n[0]).join('').toUpperCase();
  };

  constructor(
    private fb:          FormBuilder,
    private userService: UserService,
    private authService: AuthService,
    private toast:       ToastService,
    private router:      Router,
    private alertCtrl:   AlertController
  ) {
    addIcons({ personOutline, mailOutline, notificationsOutline, keyOutline, trashOutline, logOutOutline, chevronForwardOutline });
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
        this.toast.success('Nome atualizado.');
      },
      error: () => { this.isSavingName = false; this.toast.error('Erro ao salvar nome.'); }
    });
  }

  updateNotifPref(pref: 'emailEnabled' | 'pushEnabled', value: boolean): void {
    this.userService.updateProfile({ notifications: { [pref]: value } }).subscribe({
      next: resp => this.profile.set(resp.data ?? null),
      error: () => this.toast.error('Erro ao atualizar preferências.')
    });
  }

  logout(): void { this.authService.logout(); }

  async confirmDeleteAccount(): Promise<void> {
    const alert = await this.alertCtrl.create({
      header:  'Excluir Conta',
      message: 'Esta ação é permanente. Informe sua senha para confirmar.',
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
                this.toast.info('Conta excluída.');
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