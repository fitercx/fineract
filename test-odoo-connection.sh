#!/bin/bash

# Odoo Connection Test Script for Fineract
# Make sure your Fineract server is running and Odoo is configured

# Configuration
FINERACT_URL="https://localhost:8443/fineract-provider"
AUTH_HEADER="Basic bWlmb3M6cGFzc3dvcmQ="  # mifos:password in base64
TENANT_ID="default"

echo "🔧 Testing Odoo Connection via Fineract API..."
echo "=================================================="

# Function to make the API call
test_odoo_connection() {
    echo "📡 Making request to: ${FINERACT_URL}/api/v1/odoo/test"
    
    response=$(curl -k -s -w "\nHTTP_STATUS:%{http_code}" -X GET \
        "${FINERACT_URL}/api/v1/odoo/test" \
        -H "Authorization: ${AUTH_HEADER}" \
        -H "Content-Type: application/json" \
        -H "Fineract-Platform-TenantId: ${TENANT_ID}")
    
    # Extract HTTP status
    http_status=$(echo "$response" | grep "HTTP_STATUS:" | cut -d: -f2)
    
    # Extract response body
    response_body=$(echo "$response" | sed '/HTTP_STATUS:/d')
    
    echo "📊 HTTP Status: $http_status"
    echo "📋 Response:"
    echo "$response_body" | jq . 2>/dev/null || echo "$response_body"
    
    # Check results
    if [ "$http_status" = "200" ]; then
        if echo "$response_body" | grep -q '"connected":true'; then
            echo "✅ SUCCESS: Odoo connection is working!"
            return 0
        elif echo "$response_body" | grep -q '"connected":false'; then
            echo "❌ FAILED: Odoo connection failed - check your Odoo server configuration"
            return 1
        else
            echo "⚠️  WARNING: Unexpected response format"
            return 1
        fi
    else
        echo "❌ FAILED: HTTP $http_status - Check Fineract server status"
        return 1
    fi
}

# Check if jq is available for pretty JSON formatting
if ! command -v jq &> /dev/null; then
    echo "💡 Tip: Install 'jq' for better JSON formatting: brew install jq"
    echo ""
fi

# Run the test
test_odoo_connection

echo ""
echo "📝 Notes:"
echo "1. Make sure ODOO_ENABLED=true in your environment or application.properties"
echo "2. Configure your Odoo server URL, database, username, and password"
echo "3. Ensure your Odoo server is accessible from Fineract"
echo "4. Check Fineract logs for detailed error messages if the test fails"
