#!/usr/bin/env python3
"""
Script de Vérification Conformité ACPR

Vérifie automatiquement les exigences ACPR :
- Configuration LAB-FT active
- Seuils de vigilance et déclaration
- Contrôles PEP et sanctions
- Endpoint TRACFIN configuré
"""

import requests
import sys
import json
from datetime import datetime

def check_acpr_compliance(api_url="http://localhost:8092"):
    """Vérifie la conformité ACPR via l'API."""
    
    print("🏛️ Vérification Conformité ACPR")
    print("=" * 35)
    
    try:
        response = requests.get(f"{api_url}/api/compliance/acpr", timeout=30)
        
        if response.status_code == 200:
            data = response.json()
            score = data.get('score', 0)
            max_score = data.get('maxScore', 30)
            compliant = data.get('compliant', False)
            
            print(f"Score ACPR: {score}/{max_score}")
            print(f"Conforme: {'✅ OUI' if compliant else '❌ NON'}")
            
            # Critères réussis
            passed = data.get('passedCriteria', [])
            if passed:
                print("\n✅ Critères Conformes:")
                for criteria in passed:
                    print(f"  • {criteria}")
            
            # Critères en échec
            failed = data.get('failedCriteria', [])
            if failed:
                print("\n❌ Points d'Amélioration:")
                for criteria in failed:
                    print(f"  • {criteria}")
            
            # Simulation contrôle ACPR
            print("\n🎭 Simulation Contrôle ACPR...")
            sim_response = requests.post(f"{api_url}/api/compliance/simulate-acpr-control")
            if sim_response.status_code == 200:
                sim_data = sim_response.json()
                control_result = sim_data.get('simulation', {}).get('controlResult', 'UNKNOWN')
                print(f"Résultat simulation: {control_result}")
            
            return compliant
            
        else:
            print(f"❌ Erreur API: {response.status_code}")
            return False
            
    except Exception as e:
        print(f"❌ Erreur vérification ACPR: {e}")
        return False

if __name__ == "__main__":
    compliant = check_acpr_compliance()
    sys.exit(0 if compliant else 1)