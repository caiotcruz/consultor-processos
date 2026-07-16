import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
  IonButton, IonIcon, IonList, IonItem, IonLabel, IonBadge,
  IonNote, IonSpinner, AlertController
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  pencilOutline, trashOutline, pauseOutline, playOutline,
  timeOutline, calendarOutline, businessOutline, chevronBackOutline
} from 'ionicons/icons';
import { ProcessService } from '../../../services/process.service';
import { ProcessDetail } from '../../../models/process.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-process-detail',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
    IonButton, IonIcon,
    LoadingSpinnerComponent
  ],
  templateUrl: './process-detail.page.html',
  styleUrls: ['./process-detail.page.scss']
})
export class ProcessDetailPage implements OnInit {

  process = signal<ProcessDetail | null>(null);
  isLoading = signal(true);
  subscriptionId = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private processService: ProcessService,
    private toast: ToastService,
    private alertCtrl: AlertController
  ) {
    addIcons({
      pencilOutline,
      trashOutline,
      pauseOutline,
      playOutline,
      timeOutline,
      calendarOutline,
      businessOutline,
      chevronBackOutline
    });
  }

  ngOnInit(): void {
    this.subscriptionId = this.route.snapshot.paramMap.get('id')!;
    this.load();
  }

  load(): void {
    this.processService.getById(this.subscriptionId).subscribe({
      next: resp => {
        this.process.set(resp.data ?? null);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
        this.toast.error('Erro ao carregar processo.');
      }
    });
  }

  async editAlias(): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Apelido do Processo',
      cssClass: 'smooth-alert',
      inputs: [{
        name: 'alias',
        type: 'text',
        placeholder: 'Ex: Ação contra Empresa X',
        value: this.process()?.alias ?? ''
      }],
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Salvar',
          handler: data => {
            this.processService.updateAlias(this.subscriptionId, data.alias || null).subscribe({
              next: resp => {
                this.process.set(resp.data ?? null);
                this.toast.success('Apelido salvo.');
              },
              error: () => this.toast.error('Erro ao salvar apelido.')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  toggleActive(): void {
    const obs = this.process()?.active
        ? this.processService.deactivate(this.subscriptionId)
        : this.processService.reactivate(this.subscriptionId);

    obs.subscribe({
      next: resp => {
        this.process.set(resp.data ?? null);
        this.toast.success('Status atualizado com sucesso.');
      },
      error: () => this.toast.error('Erro ao alterar status.')
    });
  }

  async confirmDelete(): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Remover Processo',
      message: 'Você deixará de receber atualizações sobre este processo. Essa ação é definitiva.',
      cssClass: 'smooth-alert',
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Remover',
          role: 'destructive',
          handler: () => {
            this.processService.delete(this.subscriptionId).subscribe({
              next: () => {
                this.toast.success('Processo removido.');
                this.router.navigate(['/processes']);
              },
              error: () => this.toast.error('Erro ao remover processo.')
            });
          }
        }
      ]
    });
    await alert.present();
  }
}