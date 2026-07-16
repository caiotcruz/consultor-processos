import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonButtons, IonButton,
  IonIcon, IonFab, IonFabButton, IonList, IonItem, IonLabel,
  IonBadge, IonRefresher, IonRefresherContent, IonSegment,
  IonSegmentButton, IonSkeletonText, IonNote
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { add, personCircleOutline, refreshOutline, documentTextOutline, chevronForwardOutline, alertCircleOutline } from 'ionicons/icons';
import { ProcessService } from '../../../services/process.service';
import { ProcessSummary } from '../../../models/process.model';
import { ToastService } from '../../../core/services/toast.service';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-process-list',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonButtons, IonButton,
    IonIcon, IonFab, IonFabButton, IonLabel,
    IonRefresher, IonRefresherContent, IonSegment,
    IonSegmentButton, IonSkeletonText, FormsModule
  ],
  templateUrl: './process-list.page.html',
  styleUrls: ['./process-list.page.scss']
})
export class ProcessListPage implements OnInit {

  processes = signal<ProcessSummary[]>([]);
  isLoading = signal(true);
  activeFilter = 'all';

  constructor(
    private processService: ProcessService,
    private toast: ToastService
  ) {
    addIcons({ add, personCircleOutline, refreshOutline, documentTextOutline, chevronForwardOutline, alertCircleOutline });
  }

  ngOnInit(): void { 
    this.load(); 
  }

  load(event?: any): void {
    this.isLoading.set(!event);
    const params = this.activeFilter === 'all' ? {}
        : { active: this.activeFilter === 'true' };

    this.processService.list(params).subscribe({
      next: resp => {
        this.processes.set(resp.data ?? []);
        event?.target.complete();
        this.isLoading.set(false);
      },
      error: () => {
        event?.target.complete();
        this.isLoading.set(false);
        this.toast.error('Erro ao carregar processos.');
      }
    });
  }

  onFilterChange(): void { 
    this.load(); 
  }

  statusLabel(status: string): string {
    return { PENDING: 'Pendente', OK: 'Ativo', ERROR: 'Erro', BLOCKED: 'Bloqueado' }[status] ?? status;
  }
}