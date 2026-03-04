#!/usr/bin/env python3
"""
Script de Vérification Conformité RGPD

Vérifie automatiquement les exigences RGPD :
- DPO désigné et contactable
- Durées de rétention configurées
- Droits des personnes implémentés
- Consentement et bases légales
"""

import requests
import sys
import json
from datetime import datetime

def check_rgpd_compliance(api_url="http://localhost:8092"):
    """Vérifie la conformité RGPD via l'API."""
    
    print("🔒 Vérification Conformité RGPD")
    print("=" * 35)
    
    try:
        response = requests.get(f"{api_url}/api/compliance/rgpd", timeout=30)
        
        if response.status_code == 200:
            data = response.json()
            score = data.get('score', 0)
            max_score = data.get('maxScore', 25)
            compliant = data.get('compliant', False)
            
            print(f"Score RGPD: {score}/{max_score}")
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
            
            return compliant
            
        else:
            print(f"❌ Erreur API: {response.status_code}")
            return False
            
    except Exception as e:
        print(f"❌ Erreur vérification RGPD: {e}")
        return False

if __name__ == "__main__":
    compliant = check_rgpd_compliance()
    sys.exit(0 if compliant else 1)