import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
  IonBadge, IonNote, IonInfiniteScroll, IonInfiniteScrollContent,
  IonRefresher, IonRefresherContent, IonIcon
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { mailOutline, phonePortraitOutline, alertCircleOutline, notificationsOffOutline, chevronBackOutline } from 'ionicons/icons';
import { NotificationService } from '../../../services/notification.service';
import { NotificationHistoryItem } from '../../../models/notification.model';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-notification-history',
  standalone: true,
  imports: [
    CommonModule,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
    IonInfiniteScroll, IonInfiniteScrollContent,
    IonRefresher, IonRefresherContent, IonIcon,
    LoadingSpinnerComponent, EmptyStateComponent
  ],
  templateUrl: './notification-history.page.html',
  styleUrls: ['./notification-history.page.scss']
})
export class NotificationHistoryPage implements OnInit {

  items = signal<NotificationHistoryItem[]>([]);
  isLoading = signal(true);
  hasMore = false;
  private page = 0;

  constructor(
    private notifService: NotificationService,
    private toast: ToastService
  ) {
    addIcons({ mailOutline, phonePortraitOutline, alertCircleOutline, notificationsOffOutline, chevronBackOutline });
  }

  ngOnInit(): void { 
    this.load(); 
  }

  load(): void {
    this.notifService.getHistory(this.page).subscribe({
      next: resp => {
        const content = resp.data?.content ?? [];
        this.items.update(i => [...i, ...content]);
        this.hasMore = content.length === 20;
        this.isLoading.set(false);
      },
      error: () => { 
        this.isLoading.set(false); 
        this.toast.error('Erro ao carregar notificações.'); 
      }
    });
  }

  loadMore(event: any): void {
    this.page++;
    this.notifService.getHistory(this.page).subscribe({
      next: resp => {
        this.items.update(i => [...i, ...(resp.data?.content ?? [])]);
        this.hasMore = (resp.data?.content?.length ?? 0) === 20;
        event.target.complete();
      },
      error: () => event.target.complete()
    });
  }

  resetAndLoad(event?: any): void {
    this.page = 0;
    this.items.set([]);
    this.notifService.getHistory(0).subscribe({
      next: resp => {
        this.items.set(resp.data?.content ?? []);
        this.hasMore = (resp.data?.content?.length ?? 0) === 20;
        event?.target.complete();
      },
      error: () => event?.target.complete()
    });
  }

  getChannelIcon(channel: string): string {
    return channel === 'EMAIL' ? 'mail-outline' : 'phone-portable-outline';
  }

  statusLabel(s: string): string { 
    return { SENT: 'Enviado', FAILED: 'Falhou', SKIPPED: 'Ignorado' }[s] ?? s; 
  }
}