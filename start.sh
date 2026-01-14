#!/bin/bash

################################################################################
# TFG FILE SEARCH - PROFESSIONAL LAUNCHER
# Version: 2.0
# Author: Rodrigo Allende Rial
# Description: Production-grade persistent launcher with state management
################################################################################

set -o pipefail  # Fail on pipe errors, but continue script

# ============================================================================
# CONFIGURATION
# ============================================================================

STATE_FILE="$HOME/.tfg-filesearch"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/tfg-filesearch"
FRONTEND_DIR="$SCRIPT_DIR/tfg-filesearch-gui"
BACKEND_JAR="$BACKEND_DIR/target/filesearch-1.0.0-jar-with-dependencies.jar"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ============================================================================
# STATE MANAGEMENT
# ============================================================================

# Initialize state file if it doesn't exist
init_state() {
    if [ ! -f "$STATE_FILE" ]; then
        cat > "$STATE_FILE" << EOF
last_index=filesearch
last_path=$HOME/Documents
last_action=none
frontend_pid=0
frontend_running=false
backend_built=false
EOF
    fi
}

# Read state variable
get_state() {
    local key="$1"
    grep "^${key}=" "$STATE_FILE" 2>/dev/null | cut -d'=' -f2-
}

# Write state variable
set_state() {
    local key="$1"
    local value="$2"
    
    if [ -f "$STATE_FILE" ]; then
        # Update or add the key
        if grep -q "^${key}=" "$STATE_FILE"; then
            sed -i.bak "s|^${key}=.*|${key}=${value}|" "$STATE_FILE"
            rm -f "${STATE_FILE}.bak"
        else
            echo "${key}=${value}" >> "$STATE_FILE"
        fi
    fi
}

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

# Resolve path with tilde expansion
resolve_path() {
    local path="$1"
    # Expand tilde and resolve to absolute path
    eval echo "$path"
}

# Print colored message
print_msg() {
    local type="$1"
    local msg="$2"
    
    case "$type" in
        success) echo -e "${GREEN}[OK]${NC} $msg" ;;
        error)   echo -e "${RED}[ERROR]${NC} $msg" ;;
        warning) echo -e "${YELLOW}[WARN]${NC} $msg" ;;
        info)    echo -e "${CYAN}[INFO]${NC} $msg" ;;
        *)       echo "$msg" ;;
    esac
}

# Print section header
print_header() {
    echo ""
    echo -e "${BLUE}---------------------------------------------------------------${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}---------------------------------------------------------------${NC}"
    echo ""
}

# Check if frontend is running
is_frontend_running() {
    local pid=$(get_state "frontend_pid")
    if [ "$pid" != "0" ] && kill -0 "$pid" 2>/dev/null; then
        return 0  # Running
    else
        set_state "frontend_running" "false"
        set_state "frontend_pid" "0"
        return 1  # Not running
    fi
}

# ============================================================================
# SERVICE CHECKS
# ============================================================================

check_elasticsearch() {
    print_msg "info" "Checking Elasticsearch..."
    
    if curl -s http://localhost:9200 > /dev/null 2>&1; then
        print_msg "success" "Elasticsearch is running"
        return 0
    else
        print_msg "warning" "Elasticsearch is not running"
        echo ""
        echo "To start Elasticsearch:"
        echo "   brew services start elasticsearch-full"
        echo ""
        read -p "Press Enter to continue..."
        return 1
    fi
}

check_backend() {
    print_msg "info" "Checking backend..."
    
    if [ ! -f "$BACKEND_JAR" ]; then
        print_msg "warning" "Backend JAR not found"
        print_msg "info" "Building backend automatically..."
        
        cd "$BACKEND_DIR" || return 1
        
        if command -v mvn &> /dev/null; then
            mvn clean package -DskipTests
        elif [ -f "$HOME/.sdkman/candidates/maven/current/bin/mvn" ]; then
            "$HOME/.sdkman/candidates/maven/current/bin/mvn" clean package -DskipTests
        else
            print_msg "error" "Maven not found. Please install Maven first."
            return 1
        fi
        
        cd "$SCRIPT_DIR" || return 1
        
        if [ -f "$BACKEND_JAR" ]; then
            set_state "backend_built" "true"
            print_msg "success" "Backend build completed"
            return 0
        else
            print_msg "error" "Failed to build backend"
            return 1
        fi
    else
        set_state "backend_built" "true"
        print_msg "success" "Backend ready"
        return 0
    fi
}

check_frontend() {
    print_msg "info" "Checking frontend..."
    
    if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
        print_msg "warning" "Frontend dependencies not installed"
        print_msg "info" "Installing dependencies..."
        
        cd "$FRONTEND_DIR" || return 1
        npm install
        cd "$SCRIPT_DIR" || return 1
    fi
    
    print_msg "success" "Frontend ready"
    return 0
}

# ============================================================================
# BACKEND WRAPPER
# ============================================================================

backend() {
    if [ ! -f "$BACKEND_JAR" ]; then
        print_msg "error" "Backend not built"
        check_backend || return 1
    fi
    
    java -jar "$BACKEND_JAR" "$@"
    return $?
}

# ============================================================================
# MENU ACTIONS
# ============================================================================

action_launch_frontend() {
    print_header "LAUNCH FRONTEND GUI"
    
    if is_frontend_running; then
        local pid=$(get_state "frontend_pid")
        print_msg "success" "Frontend already running (PID: $pid)"
        echo ""
        read -p "Press Enter to continue..."
        return 0
    fi
    
    check_frontend
    
    print_msg "info" "Starting frontend..."
    echo ""
    echo "Press Ctrl+C to stop the frontend and return to menu"
    echo ""
    
    cd "$FRONTEND_DIR" || return 1
    
    # Launch in background with trap for cleanup
    npm run dev &
    local pid=$!
    
    set_state "frontend_pid" "$pid"
    set_state "frontend_running" "true"
    set_state "last_action" "launch_frontend"
    
    # Wait for user to stop it
    wait $pid
    
    # Cleanup state
    set_state "frontend_running" "false"
    set_state "frontend_pid" "0"
    
    cd "$SCRIPT_DIR" || return 1
}

action_index_documents() {
    print_header "INDEX DOCUMENTS"
    
    check_elasticsearch || return 0
    check_backend || return 0
    
    local last_path=$(get_state "last_path")
    
    echo ""
    echo "Last used path: ${CYAN}$last_path${NC}"
    echo ""
    read -p "Enter path to index (or press Enter for last used): " user_path
    
    if [ -z "$user_path" ]; then
        user_path="$last_path"
    fi
    
    # Resolve path with tilde expansion
    local resolved_path=$(resolve_path "$user_path")
    
    if [ ! -d "$resolved_path" ]; then
        print_msg "error" "Path does not exist: $resolved_path"
        echo ""
        read -p "Press Enter to continue..."
        return 0
    fi
    
    # Save this path for next time
    set_state "last_path" "$resolved_path"
    set_state "last_action" "index_documents"
    
    echo ""
    print_msg "info" "Indexing documents from: $resolved_path"
    echo ""
    
    backend update-index "$resolved_path"
    local exit_code=$?
    
    echo ""
    if [ $exit_code -eq 0 ]; then
        print_msg "success" "Indexing completed"
    else
        print_msg "warning" "Indexing completed with errors (exit code: $exit_code)"
    fi
    
    echo ""
    read -p "Press Enter to continue..."
}

action_search() {
    print_header "SEARCH FROM CLI"
    
    check_elasticsearch || return 0
    check_backend || return 0
    
    echo ""
    read -p "Enter search query: " query
    
    if [ -z "$query" ]; then
        print_msg "warning" "Empty query"
        echo ""
        read -p "Press Enter to continue..."
        return 0
    fi
    
    set_state "last_action" "search"
    
    echo ""
    backend search "$query"
    
    echo ""
    read -p "Press Enter to continue..."
}

action_stats() {
    print_header "INDEX STATISTICS"
    
    check_elasticsearch || return 0
    check_backend || return 0
    
    set_state "last_action" "stats"
    
    echo ""
    backend stats
    
    echo ""
    read -p "Press Enter to continue..."
}

action_show_settings() {
    print_header "LAST USED SETTINGS"
    
    local last_index=$(get_state "last_index")
    local last_path=$(get_state "last_path")
    local last_action=$(get_state "last_action")
    local frontend_running=$(get_state "frontend_running")
    local backend_built=$(get_state "backend_built")
    
    echo ""
    echo -e "${CYAN}Index:${NC}            $last_index"
    echo -e "${CYAN}Last path:${NC}        $last_path"
    echo -e "${CYAN}Last action:${NC}      $last_action"
    echo -e "${CYAN}Frontend running:${NC} $frontend_running"
    echo -e "${CYAN}Backend built:${NC}    $backend_built"
    echo ""
    echo -e "${YELLOW}State file:${NC} $STATE_FILE"
    echo ""
    
    read -p "Press Enter to continue..."
}

# ============================================================================
# MAIN MENU
# ============================================================================

show_menu() {
    clear
    
    echo -e "${BLUE}"
    echo "---------------------------------------------------------------"
    echo "         TFG FILE SEARCH LAUNCHER v2.0"
    echo "---------------------------------------------------------------"
    echo -e "${NC}"
    echo ""
    echo -e "  ${GREEN}1)${NC} Launch Frontend GUI"
    echo -e "  ${GREEN}2)${NC} Index Documents"
    echo -e "  ${GREEN}3)${NC} Search from CLI"
    echo -e "  ${GREEN}4)${NC} View Index Statistics"
    echo -e "  ${GREEN}5)${NC} Show Last Used Settings"
    echo -e "  ${RED}6)${NC} Exit"
    echo ""
    echo -e "${BLUE}---------------------------------------------------------------${NC}"
    echo ""
}

# ============================================================================
# MAIN LOOP
# ============================================================================

main() {
    # Initialize state
    init_state
    
    # Initial system check
    print_header "SYSTEM CHECK"
    check_elasticsearch
    check_backend
    check_frontend
    
    echo ""
    print_msg "success" "System ready"
    sleep 1
    
    # Main menu loop - never exits unless user chooses
    while true; do
        show_menu
        
        read -p "Enter choice [1-6]: " choice
        
        case "$choice" in
            1)
                action_launch_frontend || {
                    print_msg "warning" "Action failed, but launcher continues"
                    sleep 2
                }
                ;;
            2)
                action_index_documents || {
                    print_msg "warning" "Action failed, but launcher continues"
                    sleep 2
                }
                ;;
            3)
                action_search || {
                    print_msg "warning" "Action failed, but launcher continues"
                    sleep 2
                }
                ;;
            4)
                action_stats || {
                    print_msg "warning" "Action failed, but launcher continues"
                    sleep 2
                }
                ;;
            5)
                action_show_settings || {
                    print_msg "warning" "Action failed, but launcher continues"
                    sleep 2
                }
                ;;
            6)
                echo ""
                print_msg "info" "Shutting down launcher..."
                
                # Clean up frontend if running
                if is_frontend_running; then
                    local pid=$(get_state "frontend_pid")
                    print_msg "info" "Stopping frontend (PID: $pid)..."
                    kill "$pid" 2>/dev/null
                    set_state "frontend_running" "false"
                    set_state "frontend_pid" "0"
                fi
                
                echo ""
                print_msg "success" "Exiting."
                echo ""
                exit 0
                ;;
            *)
                print_msg "error" "Invalid choice: $choice"
                sleep 1
                ;;
        esac
    done
}

# ============================================================================
# TRAP HANDLERS
# ============================================================================

# Handle Ctrl+C gracefully
# Handle Ctrl+C gracefully
trap 'echo -e "\n${YELLOW}[WARN]${NC}  Interrupted. Returning to menu..."; sleep 1' INT

# ============================================================================
# ENTRY POINT
# ============================================================================

main "$@"
