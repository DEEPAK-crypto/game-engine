import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  Plus,
  Edit,
  Trash2,
  Play,
  Square,
  Save,
  GripVertical,
  Clock,
  CheckCircle,
  X,
  Loader2,
  Users,
  Calendar,
} from 'lucide-react';
import { api } from '@/services/api';
import { websocketService } from '@/services/websocket';
import type { Game, Question, GameStatus, GameEvent } from '@/types';

interface QuestionForm {
  questionText: string;
  options: string[];
  correctOptionIndex: number;
  durationSeconds: number;
  points: number;
}

const statusColors: Record<GameStatus, { bg: string; text: string }> = {
  DRAFT: { bg: 'bg-gray-100', text: 'text-gray-700' },
  SCHEDULED: { bg: 'bg-yellow-100', text: 'text-yellow-700' },
  ACTIVE: { bg: 'bg-green-100', text: 'text-green-700' },
  PAUSED: { bg: 'bg-orange-100', text: 'text-orange-700' },
  COMPLETED: { bg: 'bg-blue-100', text: 'text-blue-700' },
  CANCELLED: { bg: 'bg-red-100', text: 'text-red-700' },
};

export function GameDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [showQuestionModal, setShowQuestionModal] = useState(false);
  const [editingQuestion, setEditingQuestion] = useState<Question | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [livePlayerCount, setLivePlayerCount] = useState<number | null>(null);

  const { data: game, isLoading: gameLoading } = useQuery({
    queryKey: ['game', id],
    queryFn: () => api.getGame(id!),
    enabled: !!id,
  });

  const { data: questions = [], isLoading: questionsLoading } = useQuery({
    queryKey: ['questions', id],
    queryFn: () => api.getQuestions(id!),
    enabled: !!id,
  });

  const updateGame = useMutation({
    mutationFn: (data: Partial<Game>) => api.updateGame(id!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['game', id] });
      setIsEditing(false);
    },
  });

  const startGame = useMutation({
    mutationFn: () => api.startGame(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['game', id] });
    },
  });

  const endGame = useMutation({
    mutationFn: () => api.endGame(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['game', id] });
    },
  });

  const createQuestion = useMutation({
    mutationFn: (data: Partial<Question>) => api.createQuestion(id!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['questions', id] });
      setShowQuestionModal(false);
      resetQuestionForm();
    },
  });

  const updateQuestion = useMutation({
    mutationFn: ({ questionId, data }: { questionId: string; data: Partial<Question> }) =>
      api.updateQuestion(id!, questionId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['questions', id] });
      setShowQuestionModal(false);
      setEditingQuestion(null);
      resetQuestionForm();
    },
  });

  const deleteQuestion = useMutation({
    mutationFn: (questionId: string) => api.deleteQuestion(id!, questionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['questions', id] });
    },
  });

  const activateQuestion = useMutation({
    mutationFn: (questionId: string) => api.activateQuestion(id!, questionId),
  });

  const {
    register: registerGame,
    handleSubmit: handleGameSubmit,
    setValue: setGameValue,
  } = useForm<Partial<Game>>();

  const {
    register: registerQuestion,
    handleSubmit: handleQuestionSubmit,
    reset: resetQuestionForm,
    setValue: setQuestionValue,
    formState: { errors: questionErrors },
  } = useForm<QuestionForm>({
    defaultValues: {
      options: ['', '', '', ''],
      correctOptionIndex: 0,
      durationSeconds: 30,
      points: 100,
    },
  });

  // Subscribe to real-time updates
  useEffect(() => {
    if (!id || !game || game.status !== 'ACTIVE') return;

    const unsubscribe = websocketService.subscribeToGame(id, (event: GameEvent) => {
      if (event.eventType === 'PLAYER_COUNT') {
        setLivePlayerCount((event.payload as { count: number }).count);
      }
    });

    return () => unsubscribe();
  }, [id, game?.status]);

  // Set form values when game loads
  useEffect(() => {
    if (game) {
      setGameValue('title', game.title);
      setGameValue('description', game.description);
      setGameValue('maxPlayers', game.maxPlayers);
    }
  }, [game, setGameValue]);

  // Set question form values when editing
  useEffect(() => {
    if (editingQuestion) {
      setQuestionValue('questionText', editingQuestion.questionText);
      setQuestionValue('options', editingQuestion.options);
      setQuestionValue('correctOptionIndex', editingQuestion.correctOptionIndex);
      setQuestionValue('durationSeconds', editingQuestion.durationSeconds);
      setQuestionValue('points', editingQuestion.points);
    }
  }, [editingQuestion, setQuestionValue]);

  const onSaveGame = (data: Partial<Game>) => {
    updateGame.mutate(data);
  };

  const onSaveQuestion = (data: QuestionForm) => {
    const questionData = {
      ...data,
      sequenceNumber: editingQuestion?.sequenceNumber ?? questions.length + 1,
    };

    if (editingQuestion) {
      updateQuestion.mutate({ questionId: editingQuestion.id, data: questionData });
    } else {
      createQuestion.mutate(questionData);
    }
  };

  const handleDeleteQuestion = (questionId: string) => {
    if (window.confirm('Are you sure you want to delete this question?')) {
      deleteQuestion.mutate(questionId);
    }
  };

  if (gameLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="w-8 h-8 animate-spin text-primary-600" />
      </div>
    );
  }

  if (!game) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-500">Game not found</p>
        <Link to="/games" className="text-primary-600 hover:underline mt-2 inline-block">
          Back to Games
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/games')}
          className="p-2 hover:bg-gray-100 rounded-lg"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div className="flex-1">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-gray-900">{game.title}</h1>
            <span
              className={`px-3 py-1 rounded-full text-sm font-medium ${
                statusColors[game.status].bg
              } ${statusColors[game.status].text}`}
            >
              {game.status}
            </span>
          </div>
          <p className="text-gray-500">{game.description}</p>
        </div>
        <div className="flex gap-2">
          {game.status === 'DRAFT' && (
            <>
              <button
                onClick={() => setIsEditing(!isEditing)}
                className="px-4 py-2 border rounded-lg hover:bg-gray-50"
              >
                {isEditing ? 'Cancel' : 'Edit'}
              </button>
              <button
                onClick={() => startGame.mutate()}
                disabled={questions.length === 0}
                className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
              >
                <Play className="w-4 h-4 inline mr-2" />
                Start Game
              </button>
            </>
          )}
          {game.status === 'ACTIVE' && (
            <button
              onClick={() => endGame.mutate()}
              className="px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700"
            >
              <Square className="w-4 h-4 inline mr-2" />
              End Game
            </button>
          )}
        </div>
      </div>

      {/* Game Info / Edit Form */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          {isEditing ? (
            <form onSubmit={handleGameSubmit(onSaveGame)} className="bg-white rounded-xl shadow-sm p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Title</label>
                <input
                  type="text"
                  {...registerGame('title', { required: true })}
                  className="w-full px-3 py-2 border rounded-lg focus:border-primary-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <textarea
                  {...registerGame('description')}
                  rows={3}
                  className="w-full px-3 py-2 border rounded-lg focus:border-primary-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Max Players</label>
                <input
                  type="number"
                  {...registerGame('maxPlayers')}
                  className="w-full px-3 py-2 border rounded-lg focus:border-primary-500"
                />
              </div>
              <div className="flex justify-end">
                <button
                  type="submit"
                  disabled={updateGame.isPending}
                  className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700"
                >
                  <Save className="w-4 h-4 inline mr-2" />
                  Save Changes
                </button>
              </div>
            </form>
          ) : (
            <div className="bg-white rounded-xl shadow-sm p-6">
              <h2 className="text-lg font-semibold mb-4">Questions ({questions.length})</h2>
              {game.status === 'DRAFT' && (
                <button
                  onClick={() => {
                    setEditingQuestion(null);
                    resetQuestionForm();
                    setShowQuestionModal(true);
                  }}
                  className="mb-4 inline-flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700"
                >
                  <Plus className="w-4 h-4" />
                  Add Question
                </button>
              )}

              {questionsLoading ? (
                <div className="flex items-center justify-center py-8">
                  <Loader2 className="w-6 h-6 animate-spin text-primary-600" />
                </div>
              ) : questions.length === 0 ? (
                <p className="text-center py-8 text-gray-500">
                  No questions yet. Add some questions to get started.
                </p>
              ) : (
                <div className="space-y-3">
                  {questions
                    .sort((a, b) => a.sequenceNumber - b.sequenceNumber)
                    .map((question, idx) => (
                      <div
                        key={question.id}
                        className="flex items-start gap-3 p-4 border rounded-lg hover:bg-gray-50"
                      >
                        <div className="flex items-center gap-2 text-gray-400">
                          <GripVertical className="w-4 h-4" />
                          <span className="font-medium">{idx + 1}</span>
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="font-medium text-gray-900">{question.questionText}</p>
                          <div className="mt-2 flex flex-wrap gap-2">
                            {question.options.map((opt, optIdx) => (
                              <span
                                key={optIdx}
                                className={`px-2 py-1 text-sm rounded ${
                                  optIdx === question.correctOptionIndex
                                    ? 'bg-green-100 text-green-700'
                                    : 'bg-gray-100 text-gray-600'
                                }`}
                              >
                                {opt}
                                {optIdx === question.correctOptionIndex && (
                                  <CheckCircle className="w-3 h-3 inline ml-1" />
                                )}
                              </span>
                            ))}
                          </div>
                          <div className="mt-2 flex gap-4 text-sm text-gray-500">
                            <span className="flex items-center gap-1">
                              <Clock className="w-4 h-4" />
                              {question.durationSeconds}s
                            </span>
                            <span>{question.points} pts</span>
                          </div>
                        </div>
                        {game.status === 'DRAFT' && (
                          <div className="flex gap-1">
                            <button
                              onClick={() => {
                                setEditingQuestion(question);
                                setShowQuestionModal(true);
                              }}
                              className="p-2 text-gray-400 hover:text-primary-600 rounded"
                            >
                              <Edit className="w-4 h-4" />
                            </button>
                            <button
                              onClick={() => handleDeleteQuestion(question.id)}
                              className="p-2 text-gray-400 hover:text-red-600 rounded"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        )}
                        {game.status === 'ACTIVE' && question.status === 'PENDING' && (
                          <button
                            onClick={() => activateQuestion.mutate(question.id)}
                            className="px-3 py-1 bg-green-600 text-white text-sm rounded hover:bg-green-700"
                          >
                            Activate
                          </button>
                        )}
                        {question.status === 'ACTIVE' && (
                          <span className="px-3 py-1 bg-green-100 text-green-700 text-sm rounded">
                            Live
                          </span>
                        )}
                        {question.status === 'COMPLETED' && (
                          <span className="px-3 py-1 bg-gray-100 text-gray-600 text-sm rounded">
                            Done
                          </span>
                        )}
                      </div>
                    ))}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          <div className="bg-white rounded-xl shadow-sm p-6">
            <h3 className="font-semibold mb-4">Game Stats</h3>
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-gray-500 flex items-center gap-2">
                  <Users className="w-4 h-4" />
                  Players
                </span>
                <span className="font-medium">
                  {livePlayerCount ?? game.currentPlayers} / {game.maxPlayers}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-gray-500">Questions</span>
                <span className="font-medium">{questions.length}</span>
              </div>
              {game.scheduledStartTime && (
                <div className="flex items-center justify-between">
                  <span className="text-gray-500 flex items-center gap-2">
                    <Calendar className="w-4 h-4" />
                    Scheduled
                  </span>
                  <span className="font-medium">
                    {new Date(game.scheduledStartTime).toLocaleString()}
                  </span>
                </div>
              )}
              <div className="flex items-center justify-between">
                <span className="text-gray-500">Created</span>
                <span className="font-medium">
                  {new Date(game.createdAt).toLocaleDateString()}
                </span>
              </div>
            </div>
          </div>

          {game.status === 'ACTIVE' && (
            <div className="bg-green-50 border border-green-200 rounded-xl p-6">
              <h3 className="font-semibold text-green-800 mb-2">Game is Live</h3>
              <p className="text-sm text-green-600">
                Players can join and answer questions in real-time.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Question Modal */}
      {showQuestionModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between px-6 py-4 border-b sticky top-0 bg-white">
              <h2 className="text-lg font-semibold">
                {editingQuestion ? 'Edit Question' : 'Add Question'}
              </h2>
              <button
                onClick={() => {
                  setShowQuestionModal(false);
                  setEditingQuestion(null);
                  resetQuestionForm();
                }}
                className="p-2 hover:bg-gray-100 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleQuestionSubmit(onSaveQuestion)} className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Question
                </label>
                <textarea
                  {...registerQuestion('questionText', { required: 'Question text is required' })}
                  rows={2}
                  className="w-full px-3 py-2 border rounded-lg focus:border-primary-500"
                  placeholder="Enter your question"
                />
                {questionErrors.questionText && (
                  <p className="mt-1 text-sm text-red-500">{questionErrors.questionText.message}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Options (select correct answer)
                </label>
                <div className="space-y-2">
                  {[0, 1, 2, 3].map((idx) => (
                    <div key={idx} className="flex items-center gap-2">
                      <input
                        type="radio"
                        {...registerQuestion('correctOptionIndex')}
                        value={idx}
                        className="w-4 h-4 text-primary-600"
                      />
                      <input
                        type="text"
                        {...registerQuestion(`options.${idx}` as const, {
                          required: 'All options are required',
                        })}
                        className="flex-1 px-3 py-2 border rounded-lg focus:border-primary-500"
                        placeholder={`Option ${idx + 1}`}
                      />
                    </div>
                  ))}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Duration (seconds)
                  </label>
                  <input
                    type="number"
                    {...registerQuestion('durationSeconds', {
                      required: true,
                      min: 5,
                      max: 120,
                    })}
                    className="w-full px-3 py-2 border rounded-lg focus:border-primary-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Points
                  </label>
                  <input
                    type="number"
                    {...registerQuestion('points', {
                      required: true,
                      min: 10,
                      max: 1000,
                    })}
                    className="w-full px-3 py-2 border rounded-lg focus:border-primary-500"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-4">
                <button
                  type="button"
                  onClick={() => {
                    setShowQuestionModal(false);
                    setEditingQuestion(null);
                    resetQuestionForm();
                  }}
                  className="px-4 py-2 border rounded-lg hover:bg-gray-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createQuestion.isPending || updateQuestion.isPending}
                  className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50"
                >
                  {createQuestion.isPending || updateQuestion.isPending
                    ? 'Saving...'
                    : editingQuestion
                    ? 'Update Question'
                    : 'Add Question'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
