/**
 * quill VS Code Extension Entry Point
 * Activates syntax highlighting and language server for .quill files
 */

import * as path from 'path';
import { workspace, ExtensionContext } from 'vscode';
import {
    LanguageClient,
    LanguageClientOptions,
    ServerOptions,
    TransportKind
} from 'vscode-languageclient/node';

let client: LanguageClient | undefined;

export function activate(context: ExtensionContext) {
    // The server is implemented in node
    const serverModule = context.asAbsolutePath(
        path.join('out', 'server.js')
    );

    // If the extension is launched in debug mode then the debug server options are used
    // Otherwise the run options are used
    const serverOptions: ServerOptions = {
        run: { module: serverModule, transport: TransportKind.ipc },
        debug: {
            module: serverModule,
            transport: TransportKind.ipc,
            options: { execArgv: ['--nolazy', '--inspect=6009'] }
        }
    };

    // Options to control the language client
    const clientOptions: LanguageClientOptions = {
        // Register the server for quill documents
        documentSelector: [{ scheme: 'file', language: 'quill' }],
        synchronize: {
            // Notify the server about file changes to .quill files contained in the workspace
            fileEvents: workspace.createFileSystemWatcher('**/*.quill')
        }
    };

    // Create the language client and start the client
    client = new LanguageClient(
        'quill',
        'quill Language Server',
        serverOptions,
        clientOptions
    );

    // Start the client. This will also launch the server
    client.start();
}

export function deactivate(): Thenable<void> | undefined {
    if (!client) {
        return undefined;
    }
    return client.stop();
}
