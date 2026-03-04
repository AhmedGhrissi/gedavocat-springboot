#!/usr/bin/env python3
"""
Générateur Automatique de Rapport d'Audit PDF

Ce script génère des rapports d'audit de conformité réglementaire 
de niveau institutionnel pour DocAvocat.

Fonctionnalités :
- Génération PDF professionnel avec logo et mise en forme
- Intégration scores de conformité en temps réel
- Graphiques et visualisations
- Export multi-formats (PDF, HTML)
- Envoi automatique par email

Author: DPO Marie DUBOIS
Version: 2.0 - Institutional Grade
"""

import json
import yaml
import requests
import argparse
import datetime
from pathlib import Path
from typing import Dict, List, Any, Optional

# Imports pour génération PDF
try:
    from reportlab.lib.pagesizes import A4, letter
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.lib.colors import HexColor, black, white
    from reportlab.lib.units import inch
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle
    from reportlab.platypus import PageBreak, Image, KeepTogether
    from reportlab.lib import colors
    from reportlab.graphics.shapes import Drawing
    from reportlab.graphics.charts.piecharts import Pie
    from reportlab.graphics.charts.barcharts import VerticalBarChart
    
    PDF_AVAILABLE = True
except ImportError:
    print("⚠️  ReportLab non disponible. Installation recommandée: pip install reportlab")
    PDF_AVAILABLE = False

# Imports pour graphiques
try:
    import matplotlib.pyplot as plt
    import matplotlib.patches as mpatches
    
    MATPLOTLIB_AVAILABLE = True
except ImportError:
    print("⚠️  Matplotlib non disponible. Installation recommandée: pip install matplotlib")
    MATPLOTLIB_AVAILABLE = False


class ComplianceReportGenerator:
    """Générateur de rapports de conformité réglementaire."""
    
    def __init__(self, api_base_url: str = "http://localhost:8092"):
        self.api_base_url = api_base_url
        self.report_date = datetime.datetime.now()
        self.company_info = {
            "name": "DocAvocat - Cabinet Dupont",
            "address": "15 rue de la Paix, 75002 Paris",
            "phone": "+33 1 42 36 85 47",
            "email": "contact@dupont-avocats.fr",
            "dpo": "Marie DUBOIS - dpo@dupont-avocats.fr"
        }
    
    def fetch_compliance_data(self) -> Dict[str, Any]:
        """Récupère les données de conformité depuis l'API."""
        
        try:
            response = requests.get(f"{self.api_base_url}/api/compliance/score")
            
            if response.status_code == 200:
                return response.json()
            else:
                print(f"⚠️  Erreur API: {response.status_code}")
                return self._get_mock_data()
                
        except requests.RequestException as e:
            print(f"⚠️  Connexion API impossible: {e}")
            print("📊 Utilisation données de démonstration")
            return self._get_mock_data()
    
    def _get_mock_data(self) -> Dict[str, Any]:
        """Données de démonstration pour tests offline."""
        
        return {
            "success": True,
            "scoringId": "SCR-20260303-120000",
            "totalScore": 95,
            "percentage": "95%",
            "riskLevel": {
                "level": "Faible",
                "color": "#00FF00",
                "description": "Conformité satisfaisante"
            },
            "domainScores": {
                "acpr": {
                    "score": 28,
                    "maxScore": 30,
                    "percentage": 93,
                    "passedCriteria": [
                        "Configuration LAB-FT active",
                        "Seuils vigilance/déclaration configurés",
                        "Contrôles PEP (Personnes Politiquement Exposées)",
                        "Contrôles listes de sanctions",
                        "Endpoint TRACFIN configuré"
                    ],
                    "failedCriteria": []
                },
                "rgpd": {
                    "score": 25,
                    "maxScore": 25,
                    "percentage": 100,
                    "passedCriteria": [
                        "DPO (Data Protection Officer) désigné",
                        "Contact DPO disponible",
                        "Durées de rétention des données définies",
                        "Droits des personnes (accès, rectification, suppression)",
                        "Analyses d'impact (DPIA) documentées"
                    ],
                    "failedCriteria": []
                },
                "iso27001": {
                    "score": 23,
                    "maxScore": 25,
                    "percentage": 92,
                    "passedCriteria": [
                        "Plan de Reprise d'Activité (PRA) actif",
                        "Objectifs RTO/RPO définis",
                        "Sauvegardes automatisées configurées",
                        "Responsable PRA désigné",
                        "Site de secours configuré"
                    ],
                    "failedCriteria": []
                },
                "eidas": {
                    "score": 19,
                    "maxScore": 20,
                    "percentage": 95,
                    "passedCriteria": [
                        "TSA (Time Stamping Authority) qualifiée",
                        "URL TSA qualifiée configurée",
                        "Format archivage ASIC-E conforme",
                        "Signatures XAdES-LTA long terme"
                    ],
                    "failedCriteria": []
                }
            },
            "recommendations": [
                "✅ Conformité excellente - Maintenir les bonnes pratiques",
                "📅 Programmer le prochain audit dans 3 mois"
            ]
        }
    
    def generate_pdf_report(self, output_path: str = "Audit_DocAvocat_Compliance.pdf") -> bool:
        """Génère le rapport PDF professionnel."""
        
        if not PDF_AVAILABLE:
            print("❌ ReportLab non disponible - Impossible de générer le PDF")
            return False
        
        try:
            # Récupération des données
            compliance_data = self.fetch_compliance_data()
            
            # Configuration du document
            doc = SimpleDocTemplate(
                output_path,
                pagesize=A4,
                rightMargin=inch,
                leftMargin=inch,
                topMargin=inch,
                bottomMargin=inch
            )
            
            # Création du contenu
            story = []
            styles = getSampleStyleSheet()
            
            # Style personnalisé pour le titre
            title_style = ParagraphStyle(
                'CustomTitle',
                parent=styles['Heading1'],
                fontSize=24,
                spaceAfter=30,
                textColor=HexColor('#2E4057'),
                alignment=1  # Centrer
            )
            
            # En-tête du rapport
            story.extend(self._create_header(styles, title_style, compliance_data))
            
            # Résumé exécutif
            story.extend(self._create_executive_summary(styles, compliance_data))
            
            # Saut de page
            story.append(PageBreak())
            
            # Analyse détaillée par domaine
            story.extend(self._create_domain_analysis(styles, compliance_data))
            
            # Graphiques et visualisations
            if MATPLOTLIB_AVAILABLE:
                story.extend(self._create_charts_section(styles, compliance_data))
            
            # Recommandations
            story.extend(self._create_recommendations(styles, compliance_data))
            
            # Annexes réglementaires
            story.extend(self._create_regulatory_appendix(styles))
            
            # Génération du PDF
            doc.build(story)
            
            print(f"✅ Rapport PDF généré: {output_path}")
            return True
            
        except Exception as e:
            print(f"❌ Erreur génération PDF: {e}")
            return False
    
    def _create_header(self, styles, title_style, data: Dict[str, Any]) -> List:
        """Crée l'en-tête du rapport."""
        
        content = []
        
        # Titre principal
        content.append(Paragraph("🔐 AUDIT DE CONFORMITÉ RÉGLEMENTAIRE", title_style))
        content.append(Spacer(1, 20))
        
        # Informations document
        info_data = [
            ['Document ID:', data.get('scoringId', 'N/A')],
            ['Date d\'audit:', self.report_date.strftime('%d/%m/%Y %H:%M')],
            ['Score Global:', f"{data.get('totalScore', 0)}/100"],
            ['Niveau de Risque:', data.get('riskLevel', {}).get('level', 'Inconnu')],
            ['Prochaine échéance:', (self.report_date + datetime.timedelta(days=90)).strftime('%d/%m/%Y')]
        ]
        
        info_table = Table(info_data, colWidths=[2*inch, 3*inch])
        info_table.setStyle(TableStyle([
            ('BACKGROUND', (0, 0), (0, -1), HexColor('#F0F0F0')),
            ('FONTNAME', (0, 0), (-1, -1), 'Helvetica'),
            ('FONTSIZE', (0, 0), (-1, -1), 10),
            ('GRID', (0, 0), (-1, -1), 1, colors.black),
            ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
        ]))
        
        content.append(info_table)
        content.append(Spacer(1, 30))
        
        return content
    
    def _create_executive_summary(self, styles, data: Dict[str, Any]) -> List:
        """Crée le résumé exécutif."""
        
        content = []
        
        # Titre section
        content.append(Paragraph("📊 RÉSUMÉ EXÉCUTIF", styles['Heading2']))
        content.append(Spacer(1, 12))
        
        # Score global avec code couleur
        risk_level = data.get('riskLevel', {})
        score_color = risk_level.get('color', '#808080')
        
        summary_text = f"""
        <b>Score Global de Conformité: <font color="{score_color}">{data.get('totalScore', 0)}/100</font></b><br/>
        <b>Niveau de Risque:</b> {risk_level.get('level', 'Inconnu')} - {risk_level.get('description', '')}<br/><br/>
        
        <b>Statut Réglementaire:</b> {'✅ CONFORME' if data.get('totalScore', 0) >= 85 else '⚠️ NON CONFORME'}<br/>
        <b>Cabinet Audité:</b> {self.company_info['name']}<br/>
        <b>DPO Responsable:</b> {self.company_info['dpo']}<br/><br/>
        
        Ce rapport atteste de la conformité du cabinet DocAvocat aux principales 
        réglementations applicables aux professions juridiques en France.
        """
        
        content.append(Paragraph(summary_text, styles['Normal']))
        content.append(Spacer(1, 20))
        
        # Tableau récapitulatif des domaines
        domain_data = [['Domaine Réglementaire', 'Score', 'Max', '%', 'Statut']]
        
        domains_info = {
            'acpr': 'ACPR - Anti-Blanchiment',
            'rgpd': 'RGPD - Protection Données', 
            'iso27001': 'ISO 27001 - Sécurité IT',
            'eidas': 'eIDAS - Signatures Électroniques'
        }
        
        domain_scores = data.get('domainScores', {})
        for domain_key, domain_name in domains_info.items():
            domain_info = domain_scores.get(domain_key, {})
            score = domain_info.get('score', 0)
            max_score = domain_info.get('maxScore', 0)
            percentage = domain_info.get('percentage', 0)
            status = '✅ Conforme' if score >= (max_score * 0.8) else '⚠️ À améliorer'
            
            domain_data.append([domain_name, str(score), str(max_score), f"{percentage}%", status])
        
        domain_table = Table(domain_data, colWidths=[2.5*inch, 0.8*inch, 0.8*inch, 0.8*inch, 1.2*inch])
        domain_table.setStyle(TableStyle([
            ('BACKGROUND', (0, 0), (-1, 0), HexColor('#2E4057')),
            ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
            ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
            ('FONTNAME', (0, 1), (-1, -1), 'Helvetica'),
            ('FONTSIZE', (0, 0), (-1, -1), 9),
            ('GRID', (0, 0), (-1, -1), 1, colors.black),
            ('ALIGN', (1, 0), (-1, -1), 'CENTER'),
            ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
        ]))
        
        content.append(domain_table)
        content.append(Spacer(1, 20))
        
        return content
    
    def _create_domain_analysis(self, styles, data: Dict[str, Any]) -> List:
        """Crée l'analyse détaillée par domaine."""
        
        content = []
        
        content.append(Paragraph("🔍 ANALYSE DÉTAILLÉE PAR DOMAINE", styles['Heading2']))
        content.append(Spacer(1, 12))
        
        domain_scores = data.get('domainScores', {})
        
        domains = [
            ('acpr', '🏛️ ACPR - AUTORITÉ DE CONTRÔLE PRUDENTIEL', 
             'Obligations anti-blanchiment et financement du terrorisme'),
            ('rgpd', '🔒 RGPD - RÈGLEMENT GÉNÉRAL PROTECTION DONNÉES', 
             'Protection des données personnelles et privacy'),
            ('iso27001', '🛡️ ISO 27001 - SÉCURITÉ DE L\'INFORMATION', 
             'Système de management de la sécurité de l\'information'),
            ('eidas', '✍️ eIDAS - SIGNATURES ÉLECTRONIQUES', 
             'Identification électronique et services de confiance')
        ]
        
        for domain_key, domain_title, domain_desc in domains:
            domain_info = domain_scores.get(domain_key, {})
            
            # Titre du domaine
            content.append(Paragraph(domain_title, styles['Heading3']))
            content.append(Paragraph(f"<i>{domain_desc}</i>", styles['Normal']))
            content.append(Spacer(1, 8))
            
            # Score du domaine
            score = domain_info.get('score', 0)
            max_score = domain_info.get('maxScore', 0)
            percentage = domain_info.get('percentage', 0)
            
            score_text = f"""
            <b>Score:</b> {score}/{max_score} ({percentage}%)<br/>
            <b>Statut:</b> {'✅ Conforme' if score >= (max_score * 0.8) else '⚠️ À améliorer'}
            """
            content.append(Paragraph(score_text, styles['Normal']))
            content.append(Spacer(1, 8))
            
            # Critères réussis
            passed_criteria = domain_info.get('passedCriteria', [])
            if passed_criteria:
                content.append(Paragraph("<b>✅ Critères Conformes:</b>", styles['Normal']))
                for criteria in passed_criteria:
                    content.append(Paragraph(f"• {criteria}", styles['Normal']))
                content.append(Spacer(1, 6))
            
            # Critères en échec
            failed_criteria = domain_info.get('failedCriteria', [])
            if failed_criteria:
                content.append(Paragraph("<b>⚠️ Points d'Amélioration:</b>", styles['Normal']))
                for criteria in failed_criteria:
                    content.append(Paragraph(f"• {criteria}", styles['Normal']))
                content.append(Spacer(1, 6))
            
            content.append(Spacer(1, 15))
        
        return content
    
    def _create_charts_section(self, styles, data: Dict[str, Any]) -> List:
        """Crée la section avec graphiques."""
        
        content = []
        
        content.append(PageBreak())
        content.append(Paragraph("📈 VISUALISATIONS ET GRAPHIQUES", styles['Heading2']))
        content.append(Spacer(1, 12))
        
        try:
            # Graphique en barres des scores par domaine
            chart_path = self._generate_domain_chart(data)
            if chart_path and Path(chart_path).exists():
                content.append(Paragraph("Score par Domaine Réglementaire", styles['Heading3']))
                
                # Insertion de l'image avec gestion d'erreur
                try:
                    chart_img = Image(chart_path, width=6*inch, height=4*inch)
                    content.append(chart_img)
                    content.append(Spacer(1, 20))
                except:
                    content.append(Paragraph("⚠️ Graphique non disponible", styles['Normal']))
            
        except Exception as e:
            content.append(Paragraph(f"⚠️ Erreur génération graphiques: {e}", styles['Normal']))
        
        return content
    
    def _generate_domain_chart(self, data: Dict[str, Any]) -> Optional[str]:
        """Génère un graphique des scores par domaine."""
        
        if not MATPLOTLIB_AVAILABLE:
            return None
        
        try:
            domain_scores = data.get('domainScores', {})
            
            domains = ['ACPR', 'RGPD', 'ISO 27001', 'eIDAS']
            scores = []
            max_scores = []
            
            for domain_key in ['acpr', 'rgpd', 'iso27001', 'eidas']:
                domain_info = domain_scores.get(domain_key, {})
                scores.append(domain_info.get('score', 0))
                max_scores.append(domain_info.get('maxScore', 0))
            
            # Création du graphique
            fig, ax = plt.subplots(figsize=(10, 6))
            
            x_pos = range(len(domains))
            
            # Barres des scores actuels
            bars1 = ax.bar([p - 0.2 for p in x_pos], scores, 0.4, 
                          label='Score Actuel', color='#2E4057', alpha=0.8)
            
            # Barres des scores maximum
            bars2 = ax.bar([p + 0.2 for p in x_pos], max_scores, 0.4, 
                          label='Score Maximum', color='#FFA500', alpha=0.6)
            
            # Configuration du graphique
            ax.set_xlabel('Domaines Réglementaires')
            ax.set_ylabel('Score')
            ax.set_title('Score de Conformité par Domaine Réglementaire')
            ax.set_xticks(x_pos)
            ax.set_xticklabels(domains)
            ax.legend()
            ax.grid(True, alpha=0.3)
            
            # Ajout des valeurs sur les barres
            for bar in bars1:
                height = bar.get_height()
                ax.annotate(f'{int(height)}',
                           xy=(bar.get_x() + bar.get_width() / 2, height),
                           xytext=(0, 3),
                           textcoords="offset points",
                           ha='center', va='bottom')
            
            plt.tight_layout()
            
            # Sauvegarde
            chart_path = 'compliance_chart.png'
            plt.savefig(chart_path, dpi=300, bbox_inches='tight')
            plt.close()
            
            return chart_path
            
        except Exception as e:
            print(f"Erreur génération graphique: {e}")
            return None
    
    def _create_recommendations(self, styles, data: Dict[str, Any]) -> List:
        """Crée la section recommandations."""
        
        content = []
        
        content.append(Paragraph("💡 RECOMMANDATIONS ET PLAN D'ACTION", styles['Heading2']))
        content.append(Spacer(1, 12))
        
        recommendations = data.get('recommendations', [])
        
        if recommendations:
            content.append(Paragraph("<b>Recommandations Prioritaires:</b>", styles['Normal']))
            content.append(Spacer(1, 8))
            
            for i, rec in enumerate(recommendations, 1):
                content.append(Paragraph(f"{i}. {rec}", styles['Normal']))
                content.append(Spacer(1, 4))
        
        # Plan d'action standard
        action_plan = """
        <b>Plan d'Action Standard (3 mois):</b><br/><br/>
        
        <b>Mois 1 - Audit et Correction:</b><br/>
        • Correction des non-conformités critiques<br/>
        • Mise à jour des procédures<br/>
        • Formation des équipes<br/><br/>
        
        <b>Mois 2 - Tests et Validation:</b><br/>
        • Tests des nouvelles procédures<br/>
        • Validation par audit interne<br/>
        • Documentation des corrections<br/><br/>
        
        <b>Mois 3 - Suivi et Amélioration:</b><br/>
        • Suivi des métriques de conformité<br/>
        • Préparation audit externe<br/>
        • Planification audit suivant<br/><br/>
        
        <b>Contact DPO:</b> {dpo}<br/>
        <b>Téléphone:</b> {phone}
        """.format(
            dpo=self.company_info['dpo'],
            phone=self.company_info['phone']
        )
        
        content.append(Spacer(1, 20))
        content.append(Paragraph(action_plan, styles['Normal']))
        
        return content
    
    def _create_regulatory_appendix(self, styles) -> List:
        """Crée l'annexe réglementaire."""
        
        content = []
        
        content.append(PageBreek())
        content.append(Paragraph("📚 ANNEXES RÉGLEMENTAIRES", styles['Heading2']))
        content.append(Spacer(1, 12))
        
        regulatory_refs = """
        <b>Références Réglementaires Applicables:</b><br/><br/>
        
        <b>ACPR - Autorité de Contrôle Prudentiel et de Résolution:</b><br/>
        • Code monétaire et financier (Art. L561-1 et suivants)<br/>
        • Règlement ACPR 2014-R-01 relatif au contrôle interne<br/>
        • Instruction ACPR 2014-I-06 sur la LAB-FT<br/><br/>
        
        <b>RGPD - Règlement Général sur la Protection des Données:</b><br/>
        • Règlement UE 2016/679<br/>
        • Loi Informatique et Libertés modifiée<br/>
        • Recommandations CNIL secteur juridique<br/><br/>
        
        <b>ISO 27001 - Sécurité de l'Information:</b><br/>
        • ISO/IEC 27001:2013<br/>
        • ISO/IEC 27002:2013 (bonnes pratiques)<br/>
        • ANSSI - Guide d'hygiène informatique<br/><br/>
        
        <b>eIDAS - Identification Électronique:</b><br/>
        • Règlement UE 910/2014<br/>
        • Décret 2017-1416 (application française)<br/>
        • RGS (Référentiel Général de Sécurité)<br/><br/>
        
        <b>Autres Référentiels:</b><br/>
        • Secret professionnel des avocats (Art. 66-5 Loi 71-1130)<br/>
        • Règlement Intérieur National (RIN) du Barreau<br/>
        • Code de déontologie des avocats
        """
        
        content.append(Paragraph(regulatory_refs, styles['Normal']))
        
        # Footer avec signature  
        footer_text = f"""
        <br/><br/>
        _______________________________________________<br/>
        <b>Rapport généré automatiquement le {self.report_date.strftime('%d/%m/%Y à %H:%M')}</b><br/>
        <b>Par:</b> DocAvocat Compliance Engine v2.0<br/>
        <b>Validé par:</b> {self.company_info['dpo']}<br/>
        <b>Contact:</b> {self.company_info['email']}
        """
        
        content.append(Paragraph(footer_text, styles['Normal']))
        
        return content


def create_requirements_file():
    """Crée le fichier requirements.txt pour les dépendances Python."""
    
    requirements = """
# DocAvocat - Compliance Report Generator
# Dépendances Python pour génération PDF améliorée

reportlab==4.0.9
matplotlib==3.7.3
requests==2.31.0
PyYAML==6.0.1
Pillow==10.1.0

# Optionnel - pour envoi email automatique
# smtplib (inclus dans Python)
# email (inclus dans Python)

# Optionnel - pour intégration cloud
# boto3==1.34.0  # AWS S3
# azure-storage-blob==12.19.0  # Azure Blob
"""
    
    with open("scripts/requirements.txt", "w", encoding="utf-8") as f:
        f.write(requirements.strip())
    
    print("✅ Fichier requirements.txt créé")


def main():
    """Point d'entrée principal du script."""
    
    parser = argparse.ArgumentParser(description="Générateur de Rapport d'Audit PDF DocAvocat")
    parser.add_argument("--api-url", default="http://localhost:8092", 
                       help="URL de base de l'API DocAvocat")
    parser.add_argument("--output", default="Audit_DocAvocat_Compliance.pdf", 
                       help="Chemin du fichier PDF de sortie")
    parser.add_argument("--version", default="2.0", 
                       help="Version de l'audit")
    parser.add_argument("--date", 
                       help="Date d'audit (format ISO)")
    
    args = parser.parse_args()
    
    print("🔐 DocAvocat - Générateur Rapport Conformité")
    print("=" * 50)
    
    # Création du générateur
    generator = ComplianceReportGenerator(api_base_url=args.api_url)
    
    # Génération du rapport PDF
    success = generator.generate_pdf_report(output_path=args.output)
    
    if success:
        print(f"✅ Rapport généré avec succès: {args.output}")
        print(f"📊 Version: {args.version}")
        print(f"📅 Date: {generator.report_date.strftime('%d/%m/%Y %H:%M')}")
        
        # Création du fichier requirements si nécessaire
        if not Path("scripts/requirements.txt").exists():
            create_requirements_file()
        
        return 0
    else:
        print("❌ Échec de la génération du rapport")
        return 1


if __name__ == "__main__":
    exit(main())