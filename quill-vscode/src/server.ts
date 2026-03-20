/**
 * quill Language Server
 * Provides diagnostics and basic validation for .quill files
 */

import {
    createConnection,
    TextDocuments,
    ProposedFeatures,
    InitializeParams,
    DidChangeConfigurationNotification,
    Diagnostic,
    DiagnosticSeverity,
    DocumentDiagnosticParams,
    DocumentDiagnosticReport,
    DocumentDiagnosticReportKind,
    TextDocumentSyncKind
} from 'vscode-languageserver/node';

import { TextDocument } from 'vscode-languageserver-textdocument';
import { tokenize, LexerError } from './lexer';

// Create a connection for the server
const connection = createConnection(ProposedFeatures.all);

// Create a simple text document manager
const documents: TextDocuments<TextDocument> = new TextDocuments(TextDocument);

let hasConfigurationCapability = false;
let hasDiagnosticRelatedInformationCapability = false;

connection.onInitialize((params: InitializeParams) => {
    const capabilities = params.capabilities;

    // Does the client support the `workspace/configuration` request?
    hasConfigurationCapability = !!(
        capabilities.workspace && !!capabilities.workspace.configuration
    );
    hasDiagnosticRelatedInformationCapability = !!(
        capabilities.textDocument &&
        capabilities.textDocument.publishDiagnostics &&
        capabilities.textDocument.publishDiagnostics.relatedInformation
    );

    return {
        capabilities: {
            textDocumentSync: TextDocumentSyncKind.Incremental,
            diagnosticProvider: {
                interFileDependencies: false,
                workspaceDiagnostics: false
            }
        }
    };
});

connection.onInitialized(() => {
    if (hasConfigurationCapability) {
        // Register for configuration changes
        connection.client.register(
            DidChangeConfigurationNotification.type,
            undefined
        );
    }
});

interface quillSettings {
    maxNumberOfProblems: number;
}

const defaultSettings: quillSettings = { maxNumberOfProblems: 1000 };
let globalSettings: quillSettings = defaultSettings;

connection.onDidChangeConfiguration(change => {
    if (hasConfigurationCapability) {
        // Reset all cached document settings
        globalSettings = defaultSettings;
    } else {
        globalSettings = change.settings.quill ?? defaultSettings;
    }
    // Revalidate all open text documents
    documents.all().forEach(validateDocument);
});

function getDiagnostics(errors: LexerError[], maxProblems: number): Diagnostic[] {
    const diagnostics: Diagnostic[] = [];
    const problems = Math.min(errors.length, maxProblems);

    for (let i = 0; i < problems; i++) {
        const error = errors[i];
        const diagnostic: Diagnostic = {
            severity: DiagnosticSeverity.Error,
            range: {
                start: { line: error.line - 1, character: error.column },
                end: { line: error.line - 1, character: error.column + error.length }
            },
            message: error.message,
            source: 'quill'
        };

        if (hasDiagnosticRelatedInformationCapability) {
            diagnostic.relatedInformation = [
                {
                    location: {
                        uri: '',
                        range: diagnostic.range
                    },
                    message: 'Syntax error detected by quill lexer'
                }
            ];
        }

        diagnostics.push(diagnostic);
    }

    return diagnostics;
}

async function validateDocument(textDocument: TextDocument): Promise<void> {
    const settings = await getDocumentSettings(textDocument.uri);
    const text = textDocument.getText();

    // Tokenize and get errors
    const { errors } = tokenize(text);

    // Convert errors to diagnostics
    const diagnostics = getDiagnostics(errors, settings.maxNumberOfProblems);

    // Send diagnostics to the client
    connection.sendDiagnostics({ uri: textDocument.uri, diagnostics });
}

async function getDocumentSettings(resource: string): Promise<quillSettings> {
    if (!hasConfigurationCapability) {
        return globalSettings;
    }
    try {
        const result = await connection.workspace.getConfiguration({
            scopeUri: resource,
            section: 'quill'
        });
        return result ?? globalSettings;
    } catch {
        return globalSettings;
    }
}

// The content of a text document has changed
documents.onDidChangeContent(change => {
    validateDocument(change.document);
});

// Handle diagnostic requests
connection.languages.diagnostics.on(
    (params: DocumentDiagnosticParams): DocumentDiagnosticReport => {
        const document = documents.get(params.textDocument.uri);
        if (document === undefined) {
            return {
                kind: DocumentDiagnosticReportKind.Full,
                items: []
            };
        }

        const text = document.getText();
        const { errors } = tokenize(text);
        const diagnostics = getDiagnostics(errors, globalSettings.maxNumberOfProblems);

        return {
            kind: DocumentDiagnosticReportKind.Full,
            items: diagnostics
        };
    }
);

// Make the text document manager listen on the connection
documents.listen(connection);

// Listen on the connection
connection.listen();
