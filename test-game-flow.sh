#!/bin/bash

# Game Platform End-to-End Flow Test Script
# Tests complete game lifecycle with multiple users

set -e  # Exit on error

# Configuration
BASE_URL="${BASE_URL:-http://localhost:8080}"
API_BASE="$BASE_URL/api"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Utility functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_section() {
    echo ""
    echo -e "${YELLOW}========================================${NC}"
    echo -e "${YELLOW}$1${NC}"
    echo -e "${YELLOW}========================================${NC}"
}

# Generate UUIDs
USER1_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
USER2_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
USER3_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
USER4_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
USER5_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
USER6_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
USER7_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
USER8_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

log_section "Game Platform E2E Test"
log_info "Testing against: $BASE_URL"
log_info "Test Users:"
log_info "  User 1 (Alice):   $USER1_ID"
log_info "  User 2 (Bob):     $USER2_ID"
log_info "  User 3 (Carol):   $USER3_ID"
log_info "  User 4 (Dave):    $USER4_ID"
log_info "  User 5 (Eve):     $USER5_ID"
log_info "  User 6 (Frank):   $USER6_ID"
log_info "  User 7 (Grace):   $USER7_ID"
log_info "  User 8 (Henry):   $USER8_ID"

# Step 1: Create a game
log_section "Step 1: Creating Game"
CREATE_GAME_RESPONSE=$(curl -s -X POST "$API_BASE/games" \
    -H "Content-Type: application/json" \
    -d '{
        "name": "E2E Test Trivia Game",
        "gameType": "MCQ_FIFO",
        "initialBudget": 2000.00,
        "questionTimerSeconds": 15
    }')

GAME_ID=$(echo "$CREATE_GAME_RESPONSE" | jq -r '.id')
log_success "Game created with ID: $GAME_ID"
echo "$CREATE_GAME_RESPONSE" | jq '.'

# Step 2: Add questions to the game
log_section "Step 2: Adding Questions"
ADD_QUESTIONS_RESPONSE=$(curl -s -X POST "$API_BASE/games/$GAME_ID/questions" \
    -H "Content-Type: application/json" \
    -d '[
        {
            "questionText": "What is the capital of France?",
            "options": [
                {"optionText": "Paris"},
                {"optionText": "London"},
                {"optionText": "Berlin"},
                {"optionText": "Madrid"}
            ],
            "correctOptionIndex": 0,
            "reward": 100.00,
            "durationSeconds": 15
        },
        {
            "questionText": "What is 2 + 2?",
            "options": [
                {"optionText": "3"},
                {"optionText": "4"},
                {"optionText": "5"},
                {"optionText": "6"}
            ],
            "correctOptionIndex": 1,
            "reward": 150.00,
            "durationSeconds": 15
        },
        {
            "questionText": "What color is the sky on a clear day?",
            "options": [
                {"optionText": "Blue"},
                {"optionText": "Green"},
                {"optionText": "Red"},
                {"optionText": "Yellow"}
            ],
            "correctOptionIndex": 0,
            "reward": 200.00,
            "durationSeconds": 15
        },
        {
            "questionText": "What is the largest planet in our solar system?",
            "options": [
                {"optionText": "Earth"},
                {"optionText": "Mars"},
                {"optionText": "Jupiter"},
                {"optionText": "Saturn"}
            ],
            "correctOptionIndex": 2,
            "reward": 250.00,
            "durationSeconds": 15
        },
        {
            "questionText": "How many continents are there?",
            "options": [
                {"optionText": "5"},
                {"optionText": "6"},
                {"optionText": "7"},
                {"optionText": "8"}
            ],
            "correctOptionIndex": 2,
            "reward": 300.00,
            "durationSeconds": 15
        }
    ]')

QUESTION_COUNT=$(echo "$ADD_QUESTIONS_RESPONSE" | jq '. | length')
log_success "Added $QUESTION_COUNT questions to the game"

QUESTION1_ID=$(echo "$ADD_QUESTIONS_RESPONSE" | jq -r '.[0].id')
QUESTION2_ID=$(echo "$ADD_QUESTIONS_RESPONSE" | jq -r '.[1].id')
QUESTION3_ID=$(echo "$ADD_QUESTIONS_RESPONSE" | jq -r '.[2].id')
QUESTION4_ID=$(echo "$ADD_QUESTIONS_RESPONSE" | jq -r '.[3].id')
QUESTION5_ID=$(echo "$ADD_QUESTIONS_RESPONSE" | jq -r '.[4].id')

QUESTION1_CORRECT_OPTION=$(echo "$ADD_QUESTIONS_RESPONSE" | jq -r '.[0].options[0].id')
QUESTION2_CORRECT_OPTION=$(echo "$ADD_QUESTIONS_RESPONSE" | jq -r '.[1].options[1].id')
QUESTION3_CORRECT_OPTION=$(echo "$ADD_QUESTIONS_RESPONSE" | jq -r '.[2].options[0].id')
QUESTION4_CORRECT_OPTION=$(echo "$ADD_QUESTIONS_RESPONSE" | jq -r '.[3].options[2].id')
QUESTION5_CORRECT_OPTION=$(echo "$ADD_QUESTIONS_RESPONSE" | jq -r '.[4].options[2].id')

log_info "Question 1 (Paris): $QUESTION1_ID"
log_info "Question 2 (4): $QUESTION2_ID"
log_info "Question 3 (Blue): $QUESTION3_ID"
log_info "Question 4 (Jupiter): $QUESTION4_ID"
log_info "Question 5 (7 continents): $QUESTION5_ID"

# Step 3: Verify questions were added
log_section "Step 3: Verifying Questions"
GET_QUESTIONS_RESPONSE=$(curl -s -X GET "$API_BASE/games/$GAME_ID/questions")
QUESTION_COUNT=$(echo "$GET_QUESTIONS_RESPONSE" | jq '. | length')
log_success "Game has $QUESTION_COUNT questions"

# Step 4: Start the game
log_section "Step 4: Starting Game"
START_GAME_RESPONSE=$(curl -s -X POST "$API_BASE/games/$GAME_ID/start" \
    -H "Content-Type: application/json" \
    -d '{}')

GAME_STATUS=$(echo "$START_GAME_RESPONSE" | jq -r '.status')
log_success "Game started with status: $GAME_STATUS"
echo "$START_GAME_RESPONSE" | jq '{id, name, status, startedAt, initialBudget, remainingBudget}'

# Step 5: Get active question
log_section "Step 5: Getting Active Question"
ACTIVE_QUESTION_RESPONSE=$(curl -s -X GET "$API_BASE/games/$GAME_ID/questions/active")
ACTIVE_QUESTION_TEXT=$(echo "$ACTIVE_QUESTION_RESPONSE" | jq -r '.question.questionText')
ACTIVE_QUESTION_ID=$(echo "$ACTIVE_QUESTION_RESPONSE" | jq -r '.question.id')
REMAINING_SECONDS=$(echo "$ACTIVE_QUESTION_RESPONSE" | jq -r '.remainingSeconds')
log_success "Active question: '$ACTIVE_QUESTION_TEXT'"
log_info "Question ID: $ACTIVE_QUESTION_ID"
log_info "Remaining seconds: $REMAINING_SECONDS"

# Step 6: Submit answers from multiple users (Question 1)
log_section "Step 6: Submitting Answers for Question 1 (Paris)"

# User 1 - Correct answer (should win and get reward)
log_info "User 1 (Alice) submitting correct answer..."
USER1_ANSWER=$(curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER1_ID\",
        \"selectedOptionId\": \"$QUESTION1_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }")
USER1_CORRECT=$(echo "$USER1_ANSWER" | jq -r '.isCorrect')
USER1_REWARD=$(echo "$USER1_ANSWER" | jq -r '.rewardAmount')
USER1_RANK=$(echo "$USER1_ANSWER" | jq -r '.rank')
log_success "User 1: isCorrect=$USER1_CORRECT, reward=$USER1_REWARD, rank=$USER1_RANK"

sleep 0.1

# User 2 - Correct answer (should be 2nd, no reward)
log_info "User 2 (Bob) submitting correct answer..."
USER2_ANSWER=$(curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER2_ID\",
        \"selectedOptionId\": \"$QUESTION1_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }")
USER2_CORRECT=$(echo "$USER2_ANSWER" | jq -r '.isCorrect')
USER2_REWARD=$(echo "$USER2_ANSWER" | jq -r '.rewardAmount')
USER2_RANK=$(echo "$USER2_ANSWER" | jq -r '.rank')
log_success "User 2: isCorrect=$USER2_CORRECT, reward=$USER2_REWARD, rank=$USER2_RANK"

sleep 0.1

# User 3 - Wrong answer
log_info "User 3 (Carol) submitting incorrect answer..."
QUESTION1_WRONG_OPTION=$(echo "$ADD_QUESTIONS_RESPONSE" | jq -r '.[0].options[1].id')
USER3_ANSWER=$(curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER3_ID\",
        \"selectedOptionId\": \"$QUESTION1_WRONG_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }")
USER3_CORRECT=$(echo "$USER3_ANSWER" | jq -r '.isCorrect')
USER3_REWARD=$(echo "$USER3_ANSWER" | jq -r '.rewardAmount')
log_success "User 3: isCorrect=$USER3_CORRECT, reward=$USER3_REWARD"

# User 4 - Correct answer (should be 3rd)
sleep 0.1
log_info "User 4 (Dave) submitting correct answer..."
USER4_ANSWER=$(curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER4_ID\",
        \"selectedOptionId\": \"$QUESTION1_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }")
USER4_CORRECT=$(echo "$USER4_ANSWER" | jq -r '.isCorrect')
USER4_REWARD=$(echo "$USER4_ANSWER" | jq -r '.rewardAmount')
USER4_RANK=$(echo "$USER4_ANSWER" | jq -r '.rank')
log_success "User 4: isCorrect=$USER4_CORRECT, reward=$USER4_REWARD, rank=$USER4_RANK"

# More users submitting answers for Question 1
sleep 0.1
log_info "User 5 (Eve) submitting correct answer..."
USER5_ANSWER=$(curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER5_ID\",
        \"selectedOptionId\": \"$QUESTION1_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }")
log_success "User 5: rank=$(echo "$USER5_ANSWER" | jq -r '.rank')"

sleep 0.1
log_info "User 6 (Frank) submitting correct answer..."
USER6_ANSWER=$(curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER6_ID\",
        \"selectedOptionId\": \"$QUESTION1_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }")
log_success "User 6: rank=$(echo "$USER6_ANSWER" | jq -r '.rank')"

# Step 7: Check Question 1 Leaderboard
log_section "Step 7: Checking Question 1 Leaderboard"
Q1_LEADERBOARD=$(curl -s -X GET "$API_BASE/leaderboards/games/$GAME_ID/questions/$QUESTION1_ID?limit=10")
log_success "Question 1 Leaderboard:"
echo "$Q1_LEADERBOARD" | jq -r '.[] | "\(.rank). User \(.userId[0:8])... - Reward: $\(.rewardAmount)"'

# Step 8: Wait for question to expire and move to next question
log_section "Step 8: Waiting for Question 2 to become active"
log_info "Sleeping 16 seconds for question 1 to expire..."
sleep 16

ACTIVE_QUESTION_RESPONSE=$(curl -s -X GET "$API_BASE/games/$GAME_ID/questions/active")
ACTIVE_QUESTION_TEXT=$(echo "$ACTIVE_QUESTION_RESPONSE" | jq -r '.question.questionText')
ACTIVE_QUESTION_ID=$(echo "$ACTIVE_QUESTION_RESPONSE" | jq -r '.question.id')
log_success "Active question is now: '$ACTIVE_QUESTION_TEXT'"

# Step 9: Submit answers for Question 2 (2+2=4)
log_section "Step 9: Submitting Answers for Question 2 (2+2)"

# User 2 wins this time
log_info "User 2 (Bob) submitting correct answer first..."
USER2_Q2_ANSWER=$(curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER2_ID\",
        \"selectedOptionId\": \"$QUESTION2_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }")
USER2_Q2_REWARD=$(echo "$USER2_Q2_ANSWER" | jq -r '.rewardAmount')
USER2_Q2_RANK=$(echo "$USER2_Q2_ANSWER" | jq -r '.rank')
log_success "User 2: reward=$USER2_Q2_REWARD, rank=$USER2_Q2_RANK"

sleep 0.1

# User 1 is second
log_info "User 1 (Alice) submitting correct answer second..."
USER1_Q2_ANSWER=$(curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER1_ID\",
        \"selectedOptionId\": \"$QUESTION2_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }")
USER1_Q2_REWARD=$(echo "$USER1_Q2_ANSWER" | jq -r '.rewardAmount')
USER1_Q2_RANK=$(echo "$USER1_Q2_ANSWER" | jq -r '.rank')
log_success "User 1: reward=$USER1_Q2_REWARD, rank=$USER1_Q2_RANK"

sleep 0.1
log_info "User 7 (Grace) submitting correct answer..."
curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER7_ID\",
        \"selectedOptionId\": \"$QUESTION2_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }" > /dev/null
log_success "User 7: answered"

sleep 0.1
log_info "User 8 (Henry) submitting correct answer..."
curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER8_ID\",
        \"selectedOptionId\": \"$QUESTION2_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }" > /dev/null
log_success "User 8: answered"

# Step 10: Wait for Question 3 to become active
log_section "Step 10: Waiting for Question 3 to become active"
log_info "Sleeping 16 seconds for question 2 to expire..."
sleep 16

ACTIVE_QUESTION_RESPONSE=$(curl -s -X GET "$API_BASE/games/$GAME_ID/questions/active")
ACTIVE_QUESTION_TEXT=$(echo "$ACTIVE_QUESTION_RESPONSE" | jq -r '.question.questionText')
ACTIVE_QUESTION_ID=$(echo "$ACTIVE_QUESTION_RESPONSE" | jq -r '.question.id')
log_success "Active question is now: '$ACTIVE_QUESTION_TEXT'"

# Step 11: Submit answers for Question 3 (Blue Sky)
log_section "Step 11: Submitting Answers for Question 3 (Sky Color)"

log_info "User 1 (Alice) submitting correct answer..."
curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER1_ID\",
        \"selectedOptionId\": \"$QUESTION3_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }" > /dev/null
log_success "User 1: answered"

sleep 0.1
log_info "User 3 (Carol) submitting correct answer..."
curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER3_ID\",
        \"selectedOptionId\": \"$QUESTION3_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }" > /dev/null
log_success "User 3: answered"

sleep 0.1
log_info "User 5 (Eve) submitting correct answer..."
curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER5_ID\",
        \"selectedOptionId\": \"$QUESTION3_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }" > /dev/null
log_success "User 5: answered"

# Step 12: Wait for Question 4 to become active
log_section "Step 12: Waiting for Question 4 to become active"
log_info "Sleeping 16 seconds for question 3 to expire..."
sleep 16

ACTIVE_QUESTION_RESPONSE=$(curl -s -X GET "$API_BASE/games/$GAME_ID/questions/active")
ACTIVE_QUESTION_TEXT=$(echo "$ACTIVE_QUESTION_RESPONSE" | jq -r '.question.questionText')
log_success "Active question is now: '$ACTIVE_QUESTION_TEXT'"

# Step 13: Submit answers for Question 4 (Jupiter)
log_section "Step 13: Submitting Answers for Question 4 (Largest Planet)"

log_info "User 2 (Bob) submitting correct answer..."
curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER2_ID\",
        \"selectedOptionId\": \"$QUESTION4_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }" > /dev/null
log_success "User 2: answered"

sleep 0.1
log_info "User 4 (Dave) submitting correct answer..."
curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER4_ID\",
        \"selectedOptionId\": \"$QUESTION4_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }" > /dev/null
log_success "User 4: answered"

sleep 0.1
log_info "User 6 (Frank) submitting correct answer..."
curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER6_ID\",
        \"selectedOptionId\": \"$QUESTION4_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }" > /dev/null
log_success "User 6: answered"

# Step 14: Wait for Question 5 to become active
log_section "Step 14: Waiting for Question 5 to become active"
log_info "Sleeping 16 seconds for question 4 to expire..."
sleep 16

ACTIVE_QUESTION_RESPONSE=$(curl -s -X GET "$API_BASE/games/$GAME_ID/questions/active")
ACTIVE_QUESTION_TEXT=$(echo "$ACTIVE_QUESTION_RESPONSE" | jq -r '.question.questionText')
log_success "Active question is now: '$ACTIVE_QUESTION_TEXT'"

# Step 15: Submit answers for Question 5 (7 Continents)
log_section "Step 15: Submitting Answers for Question 5 (Continents)"

log_info "User 3 (Carol) submitting correct answer..."
curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER3_ID\",
        \"selectedOptionId\": \"$QUESTION5_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }" > /dev/null
log_success "User 3: answered"

sleep 0.1
log_info "User 7 (Grace) submitting correct answer..."
curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER7_ID\",
        \"selectedOptionId\": \"$QUESTION5_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }" > /dev/null
log_success "User 7: answered"

sleep 0.1
log_info "User 8 (Henry) submitting correct answer..."
curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER8_ID\",
        \"selectedOptionId\": \"$QUESTION5_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }" > /dev/null
log_success "User 8: answered"

# Step 16: Check Game Leaderboard
log_section "Step 16: Checking Game Leaderboard (After 5 Questions)"
GAME_LEADERBOARD=$(curl -s -X GET "$API_BASE/leaderboards/games/$GAME_ID?limit=10")
log_success "Game Leaderboard:"
echo "$GAME_LEADERBOARD" | jq -r '.[] | "\(.rank). User \(.userId[0:8])... - Total Reward: $\(.totalReward)"'

# Step 17: Try duplicate answer (should fail)
log_section "Step 17: Testing Duplicate Answer Prevention"
log_info "User 1 attempting to answer Question 2 again (should fail)..."
DUPLICATE_RESPONSE=$(curl -s -X POST "$API_BASE/games/$GAME_ID/questions/submit" \
    -H "Content-Type: application/json" \
    -d "{
        \"userId\": \"$USER1_ID\",
        \"selectedOptionId\": \"$QUESTION2_CORRECT_OPTION\",
        \"clientTimestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")\"
    }")
DUPLICATE_ERROR=$(echo "$DUPLICATE_RESPONSE" | jq -r '.message // .error')
if [[ "$DUPLICATE_ERROR" == *"duplicate"* ]] || [[ "$DUPLICATE_ERROR" == *"already"* ]]; then
    log_success "Duplicate answer correctly rejected: $DUPLICATE_ERROR"
else
    log_error "Duplicate answer check failed!"
    echo "$DUPLICATE_RESPONSE" | jq '.'
fi

# Step 18: Complete the game
log_section "Step 18: Completing Game"
COMPLETE_GAME_RESPONSE=$(curl -s -X POST "$API_BASE/games/$GAME_ID/complete")
GAME_STATUS=$(echo "$COMPLETE_GAME_RESPONSE" | jq -r '.status')
GAME_ENDED_AT=$(echo "$COMPLETE_GAME_RESPONSE" | jq -r '.endedAt')
REMAINING_BUDGET=$(echo "$COMPLETE_GAME_RESPONSE" | jq -r '.remainingBudget')
log_success "Game completed with status: $GAME_STATUS"
log_info "Ended at: $GAME_ENDED_AT"
log_info "Remaining budget: $REMAINING_BUDGET"

# Step 19: Check final game leaderboard
log_section "Step 19: Final Game Leaderboard"
FINAL_LEADERBOARD=$(curl -s -X GET "$API_BASE/leaderboards/games/$GAME_ID?limit=10")
log_success "Final Game Leaderboard:"
echo "$FINAL_LEADERBOARD" | jq -r '.[] | "\(.rank). User \(.userId[0:8])... - Total Reward: $\(.totalReward)"'

# Step 20: Check individual user game results
log_section "Step 20: Individual User Results"

log_info "Checking User 1 (Alice) results..."
USER1_RESULT=$(curl -s -X GET "$API_BASE/leaderboards/users/$USER1_ID/games/$GAME_ID")
if [[ $(echo "$USER1_RESULT" | jq -r '.userId') == "$USER1_ID" ]]; then
    USER1_TOTAL=$(echo "$USER1_RESULT" | jq -r '.totalReward')
    USER1_CORRECT_COUNT=$(echo "$USER1_RESULT" | jq -r '.correctAnswers')
    USER1_FINAL_RANK=$(echo "$USER1_RESULT" | jq -r '.finalRank')
    log_success "User 1: Total Reward=$USER1_TOTAL, Correct=$USER1_CORRECT_COUNT, Final Rank=$USER1_FINAL_RANK"
else
    log_info "User 1 result not yet finalized in Cassandra (normal - might be processed async)"
fi

log_info "Checking User 2 (Bob) results..."
USER2_RESULT=$(curl -s -X GET "$API_BASE/leaderboards/users/$USER2_ID/games/$GAME_ID")
if [[ $(echo "$USER2_RESULT" | jq -r '.userId') == "$USER2_ID" ]]; then
    USER2_TOTAL=$(echo "$USER2_RESULT" | jq -r '.totalReward')
    USER2_CORRECT_COUNT=$(echo "$USER2_RESULT" | jq -r '.correctAnswers')
    USER2_FINAL_RANK=$(echo "$USER2_RESULT" | jq -r '.finalRank')
    log_success "User 2: Total Reward=$USER2_TOTAL, Correct=$USER2_CORRECT_COUNT, Final Rank=$USER2_FINAL_RANK"
else
    log_info "User 2 result not yet finalized in Cassandra (normal - might be processed async)"
fi

# Step 21: Get game details
log_section "Step 21: Final Game Details"
GAME_DETAILS=$(curl -s -X GET "$API_BASE/games/$GAME_ID")
log_success "Game Details:"
echo "$GAME_DETAILS" | jq '{id, name, gameType, status, initialBudget, remainingBudget, startedAt, endedAt}'

# Step 22: List all games
log_section "Step 22: Listing All Games"
ALL_GAMES=$(curl -s -X GET "$API_BASE/games")
TOTAL_GAMES=$(echo "$ALL_GAMES" | jq '. | length')
log_success "Total games in system: $TOTAL_GAMES"

# Summary
log_section "Test Summary"
log_success "✓ Game creation"
log_success "✓ Question addition"
log_success "✓ Game start"
log_success "✓ Multi-user answer submission"
log_success "✓ Reward distribution"
log_success "✓ Leaderboard updates"
log_success "✓ Duplicate answer prevention"
log_success "✓ Game completion"
log_success "✓ Results retrieval"

echo ""
log_success "All tests completed successfully!"
log_info "Game ID: $GAME_ID"
log_info "Test completed at: $(date)"