import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton, IonButtons,
  IonItem, IonLabel, IonInput, IonSelect, IonSelectOption,
  IonButton, IonText, IonSpinner, IonNote, IonIcon
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { documentTextOutline, businessOutline, bookmarkOutline, chevronBackOutline, alertCircleOutline, warningOutline } from 'ionicons/icons';
import { ProcessService } from '../../../services/process.service';
import { CourtService } from '../../../services/court.service';
import { ToastService } from '../../../core/services/toast.service';
import { CourtOption } from '../../../models/process.model';

@Component({
  selector: 'app-add-process',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonBackButton,
    IonItem, IonInput, IonSelect, IonSelectOption,
    IonText, IonSpinner, IonIcon
  ],
  templateUrl: './add-process.page.html',
  styleUrls: ['./add-process.page.scss']
})
export class AddProcessPage implements OnInit {

  form = this.fb.group({
    processNumber: ['', [Validators.required, Validators.pattern(/\d{7}-\d{2}\.\d{4}\.\d\.\d{2}\.\d{4}/)]],
    courtCode: ['', Validators.required],
    alias: ['']
  });

  courts = signal<CourtOption[]>([]);
  isLoading = false;
  errorMessage = '';
  courtUnavailableMessage = '';

  get processNumber() { return this.form.get('processNumber'); }
  get courtCode() { return this.form.get('courtCode'); }

  constructor(
    private fb: FormBuilder,
    private processService: ProcessService,
    private courtService: CourtService,
    private toast: ToastService,
    private router: Router
  ) {
    addIcons({
      documentTextOutline,
      businessOutline,
      bookmarkOutline,
      chevronBackOutline,
      alertCircleOutline,
      warningOutline
    });
  }

  ngOnInit(): void {
    this.courtService.listActive().subscribe({
      next: resp => {
        this.courts.set(resp.data ?? []);
      },
      error: () => {
        this.toast.error('Erro ao carregar tribunais ativos.');
      }
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.isLoading = true;
    this.errorMessage = '';
    this.courtUnavailableMessage = '';

    this.processService.create(this.form.value as any).subscribe({
      next: resp => {
        this.isLoading = false;
        if (resp.success) {
          this.toast.success('Processo adicionado com sucesso!');
          this.router.navigate(['/processes']);
        }
      },
      error: err => {
        this.isLoading = false;
        const code = err.error?.error?.code;
        if (code === 'SUBSCRIPTION_ALREADY_EXISTS') {
          this.errorMessage = 'Você já acompanha este processo.';
        } else if (code === 'PROCESS_LIMIT_REACHED') {
          this.errorMessage = 'Limite de processos do seu plano atingido.';
        } else if (err.status === 202) {
          this.courtUnavailableMessage =
              'O tribunal ainda não está disponível. Sua solicitação foi registrada e notificaremos quando for implementado.';
        } else {
          this.errorMessage = 'Erro ao adicionar processo. Verifique o número e tente novamente.';
        }
      }
    });
  }
}